import net.named_data.jndn.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/*
 * Produces NDN interest requests and awaits answers
 */

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

fun main(args: Array<String>) {
    Interest.setDefaultCanBePrefix(true)
    val face = Face();
    val name = Name("/esp/2/data/temperature/${System.currentTimeMillis()}");
    val interest = Interest(name);
    interest.interestLifetimeMilliseconds = 3000.0;
    val counter = Counter();

    println("Express name: ${name.toUri()}")
    face.expressInterest(interest, counter, counter);

    while (counter.callbackCount < 1) {
        face.processEvents();
        Thread.sleep(10);
    }


    println("Done.")
}