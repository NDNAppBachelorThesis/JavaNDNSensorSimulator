import net.named_data.jndn.*
import java.nio.charset.StandardCharsets

/*
 * Produces NDN interest requests and awaits answers
 */

class Counter : OnData, OnTimeout {

    var callbackCount = 0

    override fun onData(interest: Interest?, data: Data) {
        callbackCount++;
        println("Got data packet with name ${data.name.toUri()}")
        val receivedData = StandardCharsets.UTF_8.decode(data.content.buf()).toString()
        println("Received: $receivedData")
    }

    override fun onTimeout(interest: Interest?) {
        callbackCount++;
        println("Timout for interest ${interest?.name?.toUri()}")
    }

}


fun main(args: Array<String>) {
    Interest.setDefaultCanBePrefix(true)
    val face = Face();
    val name = Name("/sensor/1/test");
    val interest = Interest(name);
    interest.interestLifetimeMilliseconds = 100.0;
    val counter = Counter();
    val message = "Hallo Welt!";
    name.append(message);

    println("Express name: ${name.toUri()}")
    face.expressInterest(interest, counter, counter);

    while (counter.callbackCount < 1) {
        face.processEvents();
        Thread.sleep(10);
    }


    println("Done.")
}