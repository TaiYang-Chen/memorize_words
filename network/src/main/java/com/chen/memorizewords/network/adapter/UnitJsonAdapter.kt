package com.chen.memorizewords.network.adapter

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

class UnitJsonAdapter : JsonAdapter<Unit>() {
    override fun fromJson(reader: JsonReader): Unit {
        reader.skipValue()
        return Unit
    }

    override fun toJson(writer: JsonWriter, value: Unit?) {
        writer.nullValue()
    }
}
