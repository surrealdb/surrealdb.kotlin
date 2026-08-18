package com.surrealdb.kotlin

import kotlinx.serialization.json.JsonObject

public sealed interface SurrealAuthInput {
    public data class SignIn(public val params: JsonObject) : SurrealAuthInput
    public data class Token(public val token: String) : SurrealAuthInput
}
