import net.named_data.jndn.*
import net.named_data.jndn.security.KeyChain
import net.named_data.jndn.security.SecurityException
import net.named_data.jndn.security.identity.IdentityManager
import net.named_data.jndn.security.identity.MemoryIdentityStorage
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage
import net.named_data.jndn.transport.TcpTransport
import net.named_data.jndn.transport.UdpTransport
import net.named_data.jndn.util.Blob
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/*
 * Consumes NDN interest requests and answers with data
 */

class Sensor1Handler : OnInterestCallback {
    override fun onInterest(
        prefix: Name,
        interest: Interest,
        face: Face,
        interestFilterId: Long,
        filter: InterestFilter?
    ) {
        println("OnInterest on Sensor1Handler for ${interest.name}")
        val timestamp = interest.name.get(2).toTimestamp()
        val method = interest.name.get(3).toEscapedString()

        val temperature = 15 + (10 * Random.nextDouble())
        val response = Data(interest.name)
        val respBuffer = ByteBuffer.allocate(java.lang.Double.BYTES).putDouble(temperature)

        when (method) {
            "data" -> {
                response.content = Blob(ByteArray(java.lang.Double.BYTES) { i -> respBuffer[i] }.reversedArray())
            }
        }

        face.putData(response);
    }
}


class DiscoveryHandler : OnInterestCallback {
    override fun onInterest(
        prefix: Name,
        interest: Interest,
        face: Face,
        interestFilterId: Long,
        filter: InterestFilter?
    ) {
        println("OnInterest on DiscoveryHandler for ${interest.name}")

        val response = Data(interest.name)
        val respBuffer = ByteBuffer.allocate(java.lang.Double.BYTES).putDouble(322.69)
        response.content = Blob("hallo".encodeToByteArray())

        face.putData(response);
    }
}


fun buildTestKeyChain(): KeyChain {
    val identityStorage = MemoryIdentityStorage()
    val privateKeyStorage = MemoryPrivateKeyStorage()
    val identityManager = IdentityManager(identityStorage, privateKeyStorage)
    val keyChain = KeyChain(identityManager)
    try {
        keyChain.getDefaultCertificateName()
    } catch (e: SecurityException) {
        keyChain.createIdentity(Name("/test/identity"))
        keyChain.getIdentityManager().defaultIdentity = Name("/test/identity")
    }
    return keyChain
}


fun registerPrefixHandler(face: Face, runningCounter: AtomicInteger, name: String, handler: OnInterestCallback): Long {
    val nameObj = Name(name)

    runningCounter.incrementAndGet()
    return face.registerPrefix(
        nameObj,
        handler,
        { name ->
            runningCounter.decrementAndGet()
            throw RuntimeException("Registration failed for name '${name.toUri()}'")
        },
        { prefix, registeredPrefixId ->
            println("Successfully registered '${prefix.toUri()}' with id $registeredPrefixId")
        }
    )
}


fun main() {
    Interest.setDefaultCanBePrefix(true)
//    val face = Face(UdpTransport(), UdpTransport.ConnectionInfo("127.0.0.1"));
    val face = Face()

    val keyChain = buildTestKeyChain();
    keyChain.setFace(face);
    face.setCommandSigningInfo(keyChain, keyChain.defaultCertificateName);

    val runningCounter = AtomicInteger(0)

//    registerPrefixHandler(face, runningCounter, "/sensor/1", Sensor1Handler())
    registerPrefixHandler(face, runningCounter, "/esp/discovery", DiscoveryHandler())

    while (runningCounter.get() > 0) {
        face.processEvents();
        Thread.sleep(10);   // Prevent 100% CPU load
    }
}