package tech.sethi.pebbles.spawnevents.util

import com.google.gson.Gson
import java.io.File

class ConfigFileHandler<T>(
    private val clazz: Class<T>,
    private val file: File,
    private val gson: Gson
) {
    var config: T = clazz.getDeclaredConstructor().newInstance()

    init {
        reload()
    }

    fun reload() {
        if (file.exists()) {
            val configString = file.readText()
            config = gson.fromJson(configString, clazz)
        } else {
            file.parentFile.mkdirs()
            file.createNewFile()
            val configString = gson.toJson(config)
            file.writeText(configString)
        }
    }

    fun save() {
        val configString = gson.toJson(config)
        file.writeText(configString)
    }
}
