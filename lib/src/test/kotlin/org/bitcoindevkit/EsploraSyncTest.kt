package org.bitcoindevkit

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.kotlinbitcointools.regtesttoolbox.regenv.RegEnv
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class EsploraSyncTest {
    private val conn: Persister = Persister.newInMemory()

    @Nested
    inner class Success {
        @Test
        fun `Successful sync using a Esplora client`() {
            runBlocking {
                val regtestEnv = RegEnv.connectTo(walletName = "faucet", username = "regtest", password = "password")

                val wallet: Wallet = Wallet.createSingle(
                    descriptor = TEST_BIP86_DESCRIPTOR,
                    network = Network.REGTEST,
                    persister = conn
                )
                val newAddress = wallet.revealNextAddress(KeychainKind.EXTERNAL).address

                val txId = regtestEnv.send(newAddress.toString(), 0.12345678, 2.0)
                regtestEnv.mine(2)

                val esploraClient = EsploraClient(ESPLORA_REGTEST_URL)
                val txid = Txid.fromString(txId)

                //Check that esplora has indexed the transaction before scanning
                run {
                    repeat(8) {
                        if (runCatching { esploraClient.getTx(txid) }.isSuccess) return@run
                        delay(1.seconds)
                    }
                }
                val fullScanRequest: FullScanRequest = wallet.startFullScan().build()
                val update = esploraClient.fullScan(fullScanRequest, 10uL, 1uL)


                wallet.applyUpdate(update)
                wallet.persist(conn)

                val balance = wallet.balance().total.toSat()
                println("Balance: $balance")
                assert(balance > 0uL){
                    "Balance should be greater than zero, but was $balance"
                }
            }
        }
    }
}
