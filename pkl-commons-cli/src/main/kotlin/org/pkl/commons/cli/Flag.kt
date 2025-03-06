package org.pkl.commons.cli

data class Flag(val longName: String, val shortName: String?) {
    constructor(longName: String) : this(longName, null)

    val names
        get() = if (shortName != null) arrayOf(shortName, longName) else arrayOf(longName)
}
