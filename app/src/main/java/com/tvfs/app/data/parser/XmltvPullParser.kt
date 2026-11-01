package com.tvfs.app.data.parser

import com.tvfs.app.data.db.EpgProgramEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.Reader
import java.util.Calendar
import java.util.TimeZone

object XmltvPullParser {

    /**
     * Parses an XMLTV XML stream (such as XC-PLaPA's pruned_epg.xml) using streaming XmlPullParser.
     * Retains low memory footprint (< 5 MB) even for multi-day schedules.
     *
     * @param inputStream XMLTV stream
     * @param filterOlderThanMs Optional cutoff epoch ms to skip past programs during parsing
     * @return List of EpgProgramEntity records
     */
    fun parse(
        inputStream: InputStream,
        filterOlderThanMs: Long? = null
    ): List<EpgProgramEntity> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            if (parser != null) {
                parser.setInput(inputStream, "UTF-8")
                parseInternal(parser, filterOlderThanMs)
            } else {
                parseWithSax(inputStream, filterOlderThanMs)
            }
        } catch (_: Throwable) {
            parseWithSax(inputStream, filterOlderThanMs)
        }
    }

    fun parse(
        reader: Reader,
        filterOlderThanMs: Long? = null
    ): List<EpgProgramEntity> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            if (parser != null) {
                parser.setInput(reader)
                parseInternal(parser, filterOlderThanMs)
            } else {
                parseWithSax(reader, filterOlderThanMs)
            }
        } catch (_: Throwable) {
            parseWithSax(reader, filterOlderThanMs)
        }
    }

    private fun parseWithSax(
        inputStream: InputStream,
        filterOlderThanMs: Long?
    ): List<EpgProgramEntity> {
        val programs = ArrayList<EpgProgramEntity>(1000)
        val saxFactory = javax.xml.parsers.SAXParserFactory.newInstance()
        saxFactory.isNamespaceAware = false
        val saxParser = saxFactory.newSAXParser()

        val handler = object : org.xml.sax.helpers.DefaultHandler() {
            var currentChannelEpgId = ""
            var currentStartEpochMs = 0L
            var currentStopEpochMs = 0L
            val currentTitle = StringBuilder()
            val currentDesc = StringBuilder()
            var inProgramme = false
            var currentTag = ""

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: org.xml.sax.Attributes?) {
                currentTag = qName?.lowercase() ?: ""
                if (currentTag == "programme") {
                    inProgramme = true
                    currentChannelEpgId = attributes?.getValue("channel")?.trim().orEmpty()
                    val startAttr = attributes?.getValue("start")?.trim().orEmpty()
                    val stopAttr = attributes?.getValue("stop")?.trim().orEmpty()
                    currentStartEpochMs = parseXmltvTimestamp(startAttr) ?: 0L
                    currentStopEpochMs = parseXmltvTimestamp(stopAttr) ?: 0L
                    currentTitle.setLength(0)
                    currentDesc.setLength(0)
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inProgramme && ch != null) {
                    when (currentTag) {
                        "title" -> currentTitle.append(ch, start, length)
                        "desc" -> currentDesc.append(ch, start, length)
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = qName?.lowercase() ?: ""
                if (tag == "programme") {
                    if (inProgramme && currentChannelEpgId.isNotEmpty() && currentStopEpochMs > 0L) {
                        val isNotExpired = filterOlderThanMs == null || currentStopEpochMs >= filterOlderThanMs
                        if (isNotExpired) {
                            val titleText = currentTitle.toString().trim()
                            val descText = currentDesc.toString().trim()
                            programs.add(
                                EpgProgramEntity(
                                    channelEpgId = currentChannelEpgId,
                                    title = if (titleText.isNotEmpty()) titleText else "Program",
                                    description = descText,
                                    startEpochMs = currentStartEpochMs,
                                    stopEpochMs = currentStopEpochMs
                                )
                            )
                        }
                    }
                    inProgramme = false
                    currentChannelEpgId = ""
                    currentStartEpochMs = 0L
                    currentStopEpochMs = 0L
                    currentTitle.setLength(0)
                    currentDesc.setLength(0)
                }
                currentTag = ""
            }
        }

        saxParser.parse(inputStream, handler)
        return programs
    }

    private fun parseWithSax(
        reader: Reader,
        filterOlderThanMs: Long?
    ): List<EpgProgramEntity> {
        val programs = ArrayList<EpgProgramEntity>(1000)
        val saxFactory = javax.xml.parsers.SAXParserFactory.newInstance()
        saxFactory.isNamespaceAware = false
        val saxParser = saxFactory.newSAXParser()

        val inputSource = org.xml.sax.InputSource(reader)
        val handler = object : org.xml.sax.helpers.DefaultHandler() {
            var currentChannelEpgId = ""
            var currentStartEpochMs = 0L
            var currentStopEpochMs = 0L
            val currentTitle = StringBuilder()
            val currentDesc = StringBuilder()
            var inProgramme = false
            var currentTag = ""

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: org.xml.sax.Attributes?) {
                currentTag = qName?.lowercase() ?: ""
                if (currentTag == "programme") {
                    inProgramme = true
                    currentChannelEpgId = attributes?.getValue("channel")?.trim().orEmpty()
                    val startAttr = attributes?.getValue("start")?.trim().orEmpty()
                    val stopAttr = attributes?.getValue("stop")?.trim().orEmpty()
                    currentStartEpochMs = parseXmltvTimestamp(startAttr) ?: 0L
                    currentStopEpochMs = parseXmltvTimestamp(stopAttr) ?: 0L
                    currentTitle.setLength(0)
                    currentDesc.setLength(0)
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inProgramme && ch != null) {
                    when (currentTag) {
                        "title" -> currentTitle.append(ch, start, length)
                        "desc" -> currentDesc.append(ch, start, length)
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = qName?.lowercase() ?: ""
                if (tag == "programme") {
                    if (inProgramme && currentChannelEpgId.isNotEmpty() && currentStopEpochMs > 0L) {
                        val isNotExpired = filterOlderThanMs == null || currentStopEpochMs >= filterOlderThanMs
                        if (isNotExpired) {
                            val titleText = currentTitle.toString().trim()
                            val descText = currentDesc.toString().trim()
                            programs.add(
                                EpgProgramEntity(
                                    channelEpgId = currentChannelEpgId,
                                    title = if (titleText.isNotEmpty()) titleText else "Program",
                                    description = descText,
                                    startEpochMs = currentStartEpochMs,
                                    stopEpochMs = currentStopEpochMs
                                )
                            )
                        }
                    }
                    inProgramme = false
                    currentChannelEpgId = ""
                    currentStartEpochMs = 0L
                    currentStopEpochMs = 0L
                    currentTitle.setLength(0)
                    currentDesc.setLength(0)
                }
                currentTag = ""
            }
        }

        saxParser.parse(inputSource, handler)
        return programs
    }

    private fun parseInternal(
        parser: XmlPullParser,
        filterOlderThanMs: Long?
    ): List<EpgProgramEntity> {
        val programs = ArrayList<EpgProgramEntity>(1000)
        var eventType = parser.eventType

        var currentChannelEpgId = ""
        var currentStartEpochMs = 0L
        var currentStopEpochMs = 0L
        var currentTitle = ""
        var currentDesc = ""
        var inProgramme = false
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "programme") {
                        inProgramme = true
                        currentChannelEpgId = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
                        val startAttr = parser.getAttributeValue(null, "start")?.trim().orEmpty()
                        val stopAttr = parser.getAttributeValue(null, "stop")?.trim().orEmpty()
                        currentStartEpochMs = parseXmltvTimestamp(startAttr) ?: 0L
                        currentStopEpochMs = parseXmltvTimestamp(stopAttr) ?: 0L
                        currentTitle = ""
                        currentDesc = ""
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inProgramme) {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> {
                                    currentTitle = if (currentTitle.isEmpty()) text else "$currentTitle $text"
                                }
                                "desc" -> {
                                    currentDesc = if (currentDesc.isEmpty()) text else "$currentDesc $text"
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val endTag = parser.name
                    if (endTag == "programme") {
                        if (inProgramme && currentChannelEpgId.isNotEmpty() && currentStopEpochMs > 0L) {
                            val isNotExpired = filterOlderThanMs == null || currentStopEpochMs >= filterOlderThanMs
                            if (isNotExpired) {
                                programs.add(
                                    EpgProgramEntity(
                                        channelEpgId = currentChannelEpgId,
                                        title = if (currentTitle.isNotEmpty()) currentTitle else "Program",
                                        description = currentDesc,
                                        startEpochMs = currentStartEpochMs,
                                        stopEpochMs = currentStopEpochMs
                                    )
                                )
                            }
                        }
                        inProgramme = false
                        currentChannelEpgId = ""
                        currentStartEpochMs = 0L
                        currentStopEpochMs = 0L
                        currentTitle = ""
                        currentDesc = ""
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return programs
    }

    /**
     * Parses standard XMLTV timestamps: "YYYYMMDDHHMMSS ±HHMM" or "YYYYMMDDHHMMSS" into UTC epoch ms.
     * Example: "20261101080500 +0100" -> UTC epoch milliseconds.
     */
    fun parseXmltvTimestamp(raw: String): Long? {
        val clean = raw.trim()
        if (clean.length < 14) return null
        return try {
            val year = clean.substring(0, 4).toInt()
            val month = clean.substring(4, 6).toInt()
            val day = clean.substring(6, 8).toInt()
            val hour = clean.substring(8, 10).toInt()
            val min = clean.substring(10, 12).toInt()
            val sec = clean.substring(12, 14).toInt()

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(year, month - 1, day, hour, min, sec)
            cal.set(Calendar.MILLISECOND, 0)
            var epochMs = cal.timeInMillis

            val remaining = clean.substring(14).trim()
            if (remaining.isNotEmpty() && (remaining.startsWith("+") || remaining.startsWith("-"))) {
                val isPositive = remaining.startsWith("+")
                val digitsOnly = remaining.substring(1).trim()
                if (digitsOnly.length >= 4) {
                    val tzHours = digitsOnly.substring(0, 2).toInt()
                    val tzMins = digitsOnly.substring(2, 4).toInt()
                    val offsetMs = (tzHours * 60L + tzMins) * 60_000L
                    // To convert local offset time to UTC, subtract positive offset or add negative offset
                    epochMs += if (isPositive) -offsetMs else offsetMs
                }
            }
            epochMs
        } catch (_: Exception) {
            null
        }
    }
}
