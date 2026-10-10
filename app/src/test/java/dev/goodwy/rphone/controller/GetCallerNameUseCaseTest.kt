package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.ICallerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCallerNameUseCaseTest {

    private class FakeCallerRepository(
        private val contactsByNumber: Map<String, Contact> = emptyMap()
    ) : ICallerRepository {
        override suspend fun getContactByNumber(number: String): Contact? {
            return contactsByNumber[number]
        }
    }

    @Test
    fun testReturnsLocalContactNameWhenLocalContactExists() {
        runBlocking {
            val testContact = Contact(
                id = "1",
                givenName = "Alice",
                familyName = "Smith",
                photoUri = "content://photo/1"
            )
            val fakeRepo = FakeCallerRepository(mapOf("+15550001111" to testContact))
            val useCase = GetCallerNameUseCase(fakeRepo)

            val metadata = useCase("+15550001111", cnamName = "Caller ID Name")

            assertEquals("Alice Smith", metadata.name)
            assertTrue(metadata.isLocalContact)
            assertEquals("content://photo/1", metadata.photoUri)
        }
    }

    @Test
    fun testReturnsCnamCallerNameWhenNoLocalContactExists() {
        runBlocking {
            val fakeRepo = FakeCallerRepository()
            val useCase = GetCallerNameUseCase(fakeRepo)

            val metadata = useCase("+15550002222", cnamName = "Spam Detector / Delivery Service")

            assertEquals("Spam Detector / Delivery Service", metadata.name)
            assertFalse(metadata.isLocalContact)
        }
    }

    @Test
    fun testReturnsIncomingNumberWhenNoLocalContactAndNoCnamNameProvided() {
        runBlocking {
            val fakeRepo = FakeCallerRepository()
            val useCase = GetCallerNameUseCase(fakeRepo)

            val metadata = useCase("+15550003333", cnamName = null)

            assertEquals("+15550003333", metadata.name)
            assertFalse(metadata.isLocalContact)
        }
    }

    @Test
    fun testReturnsLocalContactNameWhenContactHasOnlyMiddleNameOrJobTitle() {
        runBlocking {
            val testContact = Contact(
                id = "2",
                middleName = "Edward",
                jobTitle = "Manager"
            )
            val fakeRepo = FakeCallerRepository(mapOf("+15550004444" to testContact))
            val useCase = GetCallerNameUseCase(fakeRepo)

            val metadata = useCase("+15550004444", cnamName = "Network CNAP Name")

            assertEquals("Edward", metadata.name)
            assertTrue(metadata.isLocalContact)
        }
    }

    @Test
    fun testReturnsCnamNameWhenLocalContactHasNoNameFields() {
        runBlocking {
            val namelessContact = Contact(
                id = "3",
                phoneNumbers = listOf("+15550005555"),
                photoUri = "content://photo/3"
            )
            val fakeRepo = FakeCallerRepository(mapOf("+15550005555" to namelessContact))
            val useCase = GetCallerNameUseCase(fakeRepo)

            val metadata = useCase("+15550005555", cnamName = "Delivery Service")

            assertEquals("Delivery Service", metadata.name)
            assertFalse(metadata.isLocalContact)
            assertEquals("content://photo/3", metadata.photoUri)
        }
    }

    @Test
    fun testIgnoresBlankOrWhitespaceCnamName() {
        runBlocking {
            val fakeRepo = FakeCallerRepository()
            val useCase = GetCallerNameUseCase(fakeRepo)

            val metadata = useCase("+15550006666", cnamName = "   ")

            assertEquals("+15550006666", metadata.name)
            assertFalse(metadata.isLocalContact)
        }
    }
}
