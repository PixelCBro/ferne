package de.chris.samsungremote

data class TvProfile(
    val name: String,
    val ip: String,
    val mac: String,
    val token: String = ""
)
