package dev.goodwy.rphone.controller

import dev.goodwy.rphone.model.data.Contact
import dev.goodwy.rphone.model.`interface`.ICallerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCallerNameUseCaseTest {

    private class FakeCallerRepository(private val contacts: Map<String, Contact>) : ICallerRepository {
        override suspend fun getContactByNumber(number: String): Contact? {
            return contacts[number]
        }
    }

    @Test
    fun invoke_returnsLocalContactName_whenContactExistsWithGivenName() = runBlocking {
        val testNumber = "+15551234"
        val contact = Contact(
            id = "1",
            givenName = "John",
            familyName = "Doe"
        )
        val repo = FakeCallerRepository(mapOf(testNumber to contact))
        val useCase = GetCallerNameUseCase(repo)

        val result = useCase(testNumber, cnamName = "Spam caller")

        assertEquals("John Doe", result.name)
        assertTrue(result.isLocalContact)
    }

    @Test
    fun invoke_returnsCnamName_whenNoLocalContactExists() = runBlocking {
        val testNumber = "+15559999"
        val repo = FakeCallerRepository(emptyMap())
        val useCase = GetCallerNameUseCase(repo)

        val result = useCase(testNumber, cnamName = "Verified Business")

        assertEquals("Verified Business", result.name)
        assertFalse(result.isLocalContact)
    }

    @Test
    fun invoke_returnsFallbackNumber_whenNoContactOrCnamExists() = runBlocking {
        val testNumber = "+15550000"
        val repo = FakeCallerRepository(emptyMap())
        val useCase = GetCallerNameUseCase(repo)

        val result = useCase(testNumber, cnamName = null)

        assertEquals("+15550000", result.name)
        assertFalse(result.isLocalContact)
    }

    @Test
    fun invoke_returnsUnknownNumber_whenNumberIsEmptyAndNoCnam() = runBlocking {
        val repo = FakeCallerRepository(emptyMap())
        val useCase = GetCallerNameUseCase(repo)

        val result = useCase("", cnamName = null)

        assertEquals("Unknown Number", result.name)
        assertFalse(result.isLocalContact)
    }
}
