package com.tvfs.app

import com.tvfs.app.data.DataIngestionManager
import com.tvfs.app.data.TvfsRepository
import com.tvfs.app.data.db.ChannelEntity
import com.tvfs.app.data.db.EpgProgramEntity
import com.tvfs.app.data.parser.SeniorTvConfigParser
import com.tvfs.app.data.parser.XmltvPullParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class TvfsIngestionTest {

    private val sampleJson = """
        {
          "generated_at": "2026-11-01T01:36:12.381220+00:00",
          "total_channels": 2,
          "channels": [
            {
              "channel_number": 1,
              "name": "DIY CHANNEL [720p]",
              "stream_url": "http://tvload.win:80/live/user/pass/44336.ts",
              "logo": "https://example.com/diy.png",
              "epg_id": "DIY",
              "group": "USA: ENTERTAINMENT [1080p]"
            },
            {
              "channel_number": 2,
              "name": "A&E [720p]",
              "stream_url": "http://tvload.win:80/live/user/pass/13211.ts",
              "logo": null,
              "epg_id": "AandE.us",
              "group": "USA: ENTERTAINMENT [1080p]"
            }
          ]
        }
    """.trimIndent()

    private val sampleXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <tv generator_info_name="XC-PLPaPA Micro-EPG">
          <channel id="DIY">
            <display-name>DIY CHANNEL</display-name>
          </channel>
          <programme start="20261101080000 +0000" stop="20261101090000 +0000" channel="DIY">
            <title>Yard Crashers</title>
            <desc>Transforming a boring backyard into an oasis.</desc>
          </programme>
          <programme start="20261101090000 +0000" stop="20261101100000 +0000" channel="DIY">
            <title>House Crashers</title>
            <desc>Kitchen and bath renovations.</desc>
          </programme>
          <programme start="20261101080500 +0100" stop="20261101090500 +0100" channel="AandE.us">
            <title>Storage Wars</title>
            <desc>Auction hunters bid on forgotten lockers.</desc>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `SeniorTvConfigParser parses valid channels with metadata`() {
        val result = SeniorTvConfigParser.parse(sampleJson)
        assertEquals("2026-11-01T01:36:12.381220+00:00", result.generatedAt)
        assertEquals(2, result.totalChannels)
        assertEquals(2, result.channels.size)

        val ch1 = result.channels[0]
        assertEquals(1, ch1.channelNumber)
        assertEquals("DIY CHANNEL [720p]", ch1.name)
        assertEquals("http://tvload.win:80/live/user/pass/44336.ts", ch1.streamUrl)
        assertEquals("https://example.com/diy.png", ch1.logo)
        assertEquals("DIY", ch1.epgId)
        assertEquals("USA: ENTERTAINMENT [1080p]", ch1.groupName)

        val ch2 = result.channels[1]
        assertEquals(2, ch2.channelNumber)
        assertNull(ch2.logo)
        assertEquals("AandE.us", ch2.epgId)
    }

    @Test
    fun `XmltvPullParser parses XMLTV timestamps and offset correctly`() {
        // "20261101080000 +0000" -> UTC 2026-11-01 08:00:00
        val epochUtc = XmltvPullParser.parseXmltvTimestamp("20261101080000 +0000")
        assertNotNull(epochUtc)

        // "20261101090000 +0100" -> UTC 2026-11-01 08:00:00 (since UTC+1 is 1 hour ahead of UTC)
        val epochUtcPlus1 = XmltvPullParser.parseXmltvTimestamp("20261101090000 +0100")
        assertNotNull(epochUtcPlus1)
        assertEquals(epochUtc, epochUtcPlus1)

        // "20261101040000 -0400" -> UTC 2026-11-01 08:00:00
        val epochUtcMinus4 = XmltvPullParser.parseXmltvTimestamp("20261101040000 -0400")
        assertNotNull(epochUtcMinus4)
        assertEquals(epochUtc, epochUtcMinus4)
    }

    @Test
    fun `XmltvPullParser streams programmes and filters past items when cutoff provided`() {
        val inputStream = ByteArrayInputStream(sampleXml.toByteArray(Charsets.UTF_8))
        val programs = XmltvPullParser.parse(inputStream)
        assertEquals(3, programs.size)

        val p1 = programs[0]
        assertEquals("DIY", p1.channelEpgId)
        assertEquals("Yard Crashers", p1.title)
        assertEquals("Transforming a boring backyard into an oasis.", p1.description)

        val p3 = programs[2]
        assertEquals("AandE.us", p3.channelEpgId)
        assertEquals("Storage Wars", p3.title)

        // Test filtering programs ended before cutoff
        val cutoff = XmltvPullParser.parseXmltvTimestamp("20261101093000 +0000")!!
        val filteredStream = ByteArrayInputStream(sampleXml.toByteArray(Charsets.UTF_8))
        val filtered = XmltvPullParser.parse(filteredStream, filterOlderThanMs = cutoff)
        assertEquals(1, filtered.size)
        assertEquals("House Crashers", filtered.first().title)
    }

    @Test
    fun `TvfsRepository buildChannelFromEntity constructs channel with live progress and metadata`() {
        val entity = ChannelEntity(
            channelNumber = 1,
            name = "DIY CHANNEL",
            streamUrl = "http://example.com/stream.ts",
            logo = null,
            epgId = "DIY",
            groupName = "Lifestyle"
        )

        val channel = TvfsRepository.buildChannelFromEntity(entity, epgDao = null)
        assertEquals(1, channel.id)
        assertEquals("01", channel.number)
        assertEquals("DIY CHANNEL", channel.name)
        assertEquals("Lifestyle", channel.category)
        assertEquals("http://example.com/stream.ts", channel.streamUrl)
        assertEquals("Live Programming", channel.currentProgram)
    }
}
