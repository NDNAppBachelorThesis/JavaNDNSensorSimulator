import net.named_data.jndn.*
import net.named_data.jndn.transport.TcpTransport
import java.nio.ByteBuffer
import kotlin.system.measureTimeMillis

/*
 * Produces NDN interest requests and awaits answers
 */

abstract class InterestHandler : OnData, OnTimeout {
    private var hadTimeout = false
    abstract fun setDone()
    abstract fun isDone(): Boolean
    abstract fun abort()

    fun hadTimeout(): Boolean {
        return hadTimeout
    }

    override fun onTimeout(interest: Interest?) {
        println("Timeout for interest ${interest?.name?.toUri()}")
        hadTimeout = true
        abort()
    }

    protected fun getDataAsDouble(data: Data): Double {
        return ByteBuffer.wrap(getDataAsByteArray(data).reversedArray()).double
    }

    /**
     * Converts the data to a ByteArray
     */
    protected fun getDataAsByteArray(data: Data): ByteArray {
        if (data.content.buf() == null) {
            return ByteArray(0)
        }

        return ByteArray(data.content.size()) { i -> data.content.buf()[i] }
    }

    // new functions

    protected fun getAsByteArray(data: Data, length: Int, offset: Int): ByteArray {
        if (data.content.buf() == null) {
            return ByteArray(0)
        }

        if (length + offset > data.content.size()) {
            throw RuntimeException("Requested data too large. Size=${data.content.size()}")
        }

        return ByteArray(length) { data.content.buf()[it + offset] }
    }

    protected fun bytesToLong(byteArray: ByteArray): Long {
        return ByteBuffer.wrap(byteArray.reversedArray()).getLong()
    }

    protected fun bytesToFloat(byteArray: ByteArray): Float {
        return ByteBuffer.wrap(byteArray.reversedArray()).getFloat()
    }

    protected fun bytesToDouble(byteArray: ByteArray): Double {
        return ByteBuffer.wrap(byteArray.reversedArray()).getDouble()
    }
}

abstract class BasicInterestHandler : InterestHandler() {
    protected var finished = false

    override fun setDone() {
        finished = true
    }

    override fun isDone(): Boolean {
        return finished
    }

    override fun abort() {
        finished = true
    }
}

class GetSensorDataHandler : BasicInterestHandler() {
    var data: Double? = null

    override fun onData(interest: Interest, data: Data) {
        this.data = getDataAsDouble(data)
        setDone()
    }
}

class DiscoveryClientHandler : BasicInterestHandler() {
    var responseId: Long? = null
    var responsePaths = mutableListOf<String>()

    override fun onData(interest: Interest, data: Data) {
        println("Got data packet with name ${data.name.toUri()}")

        if (data.name.size() == 4 && data.name[3].toEscapedString() == "1") {
            println(" -> Is NFD")
        } else {
            val paths = String(getDataAsByteArray(data)).split('\u0000').filter { it.isNotEmpty() }    // Separated by 0-Byte
            println(" -> Paths: $paths")
            responsePaths.addAll(paths)
        }

        responseId = data.name[2].toEscapedString().toLongOrNull()

        setDone()
        return
    }
}

class LinkQualityHandler : BasicInterestHandler() {
    override fun onData(interest: Interest?, data: Data) {

        for (i in 0..<data.content.size() / 12) {
            val id = bytesToLong(getAsByteArray(data, 8, 12 * i + 0))
            val quality = bytesToFloat(getAsByteArray(data, 4, 12 * i + 8))

            println("$i: $id -> $quality")
        }

        setDone()
    }
}


fun performDiscovery(face: Face) {
    var timeoutCnt = 0
    val visitedIds = mutableListOf<Long>()
    val foundPaths = mutableListOf<String>()

    while (timeoutCnt < 3) {
        val name = Name("/esp/discovery")
        visitedIds.forEach {
            name.append(ByteBuffer.wrap(ByteArray(8)).putLong(it).array().reversedArray())
        }
        val interest = Interest(name)
        interest.mustBeFresh = true
        interest.interestLifetimeMilliseconds = 3000.0
        val handler = DiscoveryClientHandler()

        face.expressInterest(interest, handler, handler)

        while (!handler.isDone()) {
            face.processEvents()
            Thread.sleep(10)
        }

        if (handler.responseId == null) {
            timeoutCnt++
        } else {
            visitedIds.add(handler.responseId!!)
            foundPaths.addAll(handler.responsePaths)
        }

        Thread.sleep(25)
    }

    println("Found the following paths: ${foundPaths}")
}


fun requestLinkQuality(face: Face) {
    val name = Name("/esp/198328652539720/linkquality")
    val interest = Interest(name)
    interest.mustBeFresh = true
    interest.interestLifetimeMilliseconds = 3000.0
    val handler = LinkQualityHandler()

    face.expressInterest(interest, handler, handler)

    while (!handler.isDone()) {
        face.processEvents()
        Thread.sleep(10)
    }
}


fun spamNDNRequests(face: Face, name: String) {
    var cnt = 1
    val tStart = System.currentTimeMillis()
    var delaySum = 0L

    while (System.currentTimeMillis() - tStart < 60000) {
        val interest = Interest(name)
        interest.mustBeFresh = true
        interest.interestLifetimeMilliseconds = 2000.0

        val handler = GetSensorDataHandler()
        delaySum += measureTimeMillis {
            face.expressInterest(interest, handler, handler)
            while (!handler.isDone()) {
                face.processEvents()
                Thread.sleep(1)
            }
        }
        val remainingTime = 60 - (System.currentTimeMillis() - tStart) / 1000
        print("\r[${cnt.toString().padStart(6, '0')}] (${remainingTime.toString().padStart(2, '0')}s rem.) Response: ${handler.data}")
        cnt++
    }

    println("\nResponses/s: ${cnt / 60}, avg. delay: ${delaySum / cnt}")
}


fun spamHTTPRequests(ip: String) {
    var cnt = 1
    val tStart = System.currentTimeMillis()
    var delaySum = 0L

    while (System.currentTimeMillis() - tStart < 60000) {
        var data: String
        delaySum += measureTimeMillis {
            val r = khttp.get("http://$ip/value")
            data = r.content.decodeToString()
        }
        val remainingTime = 60 - (System.currentTimeMillis() - tStart) / 1000
        print("\r[${cnt.toString().padStart(6, '0')}] (${remainingTime.toString().padStart(2, '0')}s rem.) Response: $data")
        cnt++
    }

    println("\nResponses/s: ${cnt / 60}, avg. delay: ${delaySum / cnt}")
}



fun main(args: Array<String>) {
    Interest.setDefaultCanBePrefix(true)
    val face = Face(TcpTransport(), TcpTransport.ConnectionInfo("192.168.178.179"))

//    performDiscovery(face)
    spamNDNRequests(face, "/esp/233585120353436/data/temperature")
//    spamHTTPRequests("192.168.178.172")
//    requestLinkQuality(face)

    Thread.sleep(100)

    println("Done.")
}