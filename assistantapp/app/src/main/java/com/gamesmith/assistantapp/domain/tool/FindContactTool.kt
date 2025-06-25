package com.gamesmith.assistantapp.domain.tool

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.CoroutineScope

class FindContactTool : NativeTool {
    override val name: String = "find_contact_tool"
    override val description: String = "Finds a contact by name and returns all associated phone numbers. Name matching is case-insensitive and allows slight variations. Returns an error if no contact is found.it is generally better to display contact number (in canvas) rather than speaking...choose wisely.you can let users choose if found multiple contact numbers using thee canvas tool."
    override val parametersJsonSchema: String = """{"type":"object","properties":{"name":{"type":"string","description":"The name of the contact to search for."}},"required":["name"]}"""
    override val defaultBehavior: String? = "NON_BLOCKING"
    override val defaultScheduling: String? = "INTERRUPT"

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        val name = args["name"] as? String
        if (name.isNullOrBlank()) {
            return ToolExecutionResult.Error("Missing or empty 'name' argument.")
        }
        val contentResolver = serviceContext.contentResolver
        val contacts = mutableListOf<Pair<String, List<String>>>() // Pair<DisplayName, List<PhoneNumbers>>
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = null // We'll filter in code for fuzzy match
        val selectionArgs = null
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        val contactMap = mutableMapOf<String, MutableList<String>>()
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val displayName = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx) ?: continue
                val key = displayName.trim()
                if (!contactMap.containsKey(key)) contactMap[key] = mutableListOf()
                contactMap[key]?.add(number)
            }
        }
        // Fuzzy match: case-insensitive contains or Levenshtein distance <= 2
        fun levenshtein(a: String, b: String): Int {
            val dp = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) dp[i][0] = i
            for (j in 0..b.length) dp[0][j] = j
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    dp[i][j] = if (a[i - 1].equals(b[j - 1], ignoreCase = true)) dp[i - 1][j - 1]
                    else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
            return dp[a.length][b.length]
        }
        val inputName = name.trim()
        val matches = contactMap.keys.map { contactName ->
            val dist = levenshtein(contactName, inputName)
            Triple(contactName, dist, contactMap[contactName] ?: emptyList())
        }.filter { (contactName, dist, _) ->
            contactName.contains(inputName, ignoreCase = true) || dist <= 2
        }.sortedBy { it.second }
        if (matches.isEmpty()) {
            return ToolExecutionResult.Error("No contact found matching '$name'.")
        }
        // Return all matches as a list of {contact_name, phone_numbers}
        val results = matches.map { (contactName, _, phoneNumbers) ->
            mapOf(
                "contact_name" to contactName,
                "phone_numbers" to phoneNumbers
            )
        }
        return ToolExecutionResult.Success(
            mapOf(
                "matches" to results
            )
        )
    }
} 