import net.named_data.jndn.*
import java.nio.ByteBuffer

/*
 * Produces NDN interest requests and awaits answers
 */

abstract class InterestHandler : OnData, OnTimeout {
    abstract fun isDone(): Boolean;
    abstract fun abort();

    override fun onTimeout(interest: Interest?) {
        println("Timout for interest ${interest?.name?.toUri()}")
        abort();
    }

    fun getDataAsByteBuffer(data: Data): ByteBuffer {
        return ByteBuffer.wrap(getDataAsByteArray(data).reversedArray());
    }

    fun getDataAsByteArray(data: Data): ByteArray {
        if (data.content.buf() == null) {
            return ByteArray(0)
        }

        return ByteArray(data.content.size()) { i -> data.content.buf()[i] }
    }

}

class Counter : OnData, OnTimeout {

    var callbackCount = 0

    override fun onData(interest: Interest?, data: Data) {
        callbackCount++;
        println("Got data packet with name ${data.name.toUri()}")
        if (data.content.buf() != null) {
            val buffer = ByteBuffer.wrap(ByteArray(java.lang.Double.BYTES) { i -> data.content.buf()[i] }.reversedArray());
            val receivedData = buffer.getDouble()
            println("Received: $receivedData")
        } else {
            println("Received no data")
        }
    }

    override fun onTimeout(interest: Interest?) {
        callbackCount++;
        println("Timout for interest ${interest?.name?.toUri()}")
    }

}

class DiscoveryClientHandler : InterestHandler() {
    private var finished = false
    var responseId: Long? = null
    var responsePaths = mutableListOf<String>()

    override fun isDone(): Boolean {
        return finished
    }

    override fun abort() {
        finished = true;
    }

    override fun onData(interest: Interest, data: Data) {
        println("Got data packet with name ${data.name.toUri()}")
        val paths = String(getDataAsByteArray(data))
            .split('\u0000')
            .filter { it.isNotEmpty() }    // Separated by 0-Byte
        println("Paths: ${paths}")

        responseId = data.name[-1].toEscapedString().toLongOrNull()
        responsePaths.addAll(paths)
        finished = true

        return
    }
}


fun performDiscovery(face: Face) {
    var timeoutCnt = 0
    val visitedIds = mutableListOf<Long>()
    val foundPaths = mutableListOf<String>()

    while (timeoutCnt < 3) {
        val name = Name("/esp/discovery");
        visitedIds.forEach {
            name.append(ByteBuffer.wrap(ByteArray(8)).putLong(it).array().reversedArray())
        }
        val interest = Interest(name);
        interest.mustBeFresh = true;
        interest.interestLifetimeMilliseconds = 3000.0;
        val handler = DiscoveryClientHandler()

        face.expressInterest(interest, handler, handler);

        while (!handler.isDone()) {
            face.processEvents();
            Thread.sleep(10);
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


fun requestDiscovery(face: Face): InterestHandler {
    val name = Name("/esp/discovery");
//    name.append(ByteBuffer.wrap(ByteArray(8)).putLong(92843337030812).array().reversedArray())
//    name.append(ByteBuffer.wrap(ByteArray(8)).putLong(233585120353436).array().reversedArray())
    val interest = Interest(name);
    interest.mustBeFresh = true;
    interest.interestLifetimeMilliseconds = 3000.0;
    val handler = DiscoveryClientHandler()

    println("Express name: ${name.toUri()}")
    face.expressInterest(interest, handler, handler);

    return handler;
}


fun main(args: Array<String>) {
    Interest.setDefaultCanBePrefix(true)
    val face = Face();
//    val name = Name("/esp/2/data/temperature/${System.currentTimeMillis()}");

//    val handler = requestDiscovery(face);
//
//    while (!handler.isDone()) {
//        face.processEvents();
//        Thread.sleep(10);
//    }

    performDiscovery(face)

    Thread.sleep(10)

    println("Done.")
}