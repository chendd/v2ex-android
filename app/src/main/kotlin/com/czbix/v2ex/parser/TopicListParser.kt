package com.czbix.v2ex.parser

import com.czbix.v2ex.common.exception.FatalException
import com.czbix.v2ex.helper.JsoupObjects
import com.czbix.v2ex.model.*
import com.google.common.base.Preconditions
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.util.regex.Pattern

object TopicListParser : Parser() {
    private val PATTERN_REPLY_TIME = Pattern.compile("•\\s*(.+?)(?:\\s+•|$)")

    @JvmStatic
    fun parseDoc(doc: Document, page: Page): List<Topic> {
        val contentBox = JsoupObjects(doc).bfs("body").child("#Wrapper").child(".content").child("#Main").child(".box").first()

        if (page is Node) {
            return parseDocForNode(contentBox, page)
        } else if (page is Tab || Page.PAGE_FAV_TOPIC == page) {
            return parseDocForTab(contentBox)
        } else {
            throw IllegalArgumentException("unknown page type: " + page)
        }
    }

    private fun parseDocForTab(contentBox: Element): List<Topic> {
        val elements = JsoupObjects(contentBox).child(".item").child("table").child("tbody").child("tr")
        return elements.map {
            parseItemForTab(it)
        }
    }

    private fun parseDocForNode(contentBox: Element, node: Node): List<Topic> {
        val elements = JsoupObjects(contentBox).child("#TopicsNode").child(".cell").child("table").child("tbody").child("tr")
        return elements.map {
            parseItemForNode(it, node)
        }
    }

    private fun parseItemForTab(item: Element): Topic {
        val list = item.children()

        val topicBuilder = Topic.Builder()
        parseMember(topicBuilder, list[0])

        val ele = list[2]
        parseTitle(topicBuilder, ele)
        parseInfo(topicBuilder, ele, null)

        parseReplyCount(topicBuilder, list[3])

        return topicBuilder.createTopic()
    }

    private fun parseItemForNode(item: Element, node: Node): Topic {
        val list = item.children()

        val topicBuilder = Topic.Builder()
        parseMember(topicBuilder, list[0])

        val ele = list[2]
        parseTitle(topicBuilder, ele)
        parseInfo(topicBuilder, ele, node)

        parseReplyCount(topicBuilder, list[3])

        return topicBuilder.createTopic()
    }

    private fun parseReplyCount(topicBuilder: Topic.Builder, ele: Element) {
        val children = ele.children()
        val count: Int
        if (children.size > 0) {
            val numStr = ele.child(0).text()
            count = Integer.parseInt(numStr)
        } else {
            // do not have reply yet
            count = 0
        }
        topicBuilder.setReplyCount(count)
    }

    private fun parseInfo(topicBuilder: Topic.Builder, ele: Element, node: Node?) {
        var fade = ele
        var node = node
        fade = JsoupObjects.child(fade, ".fade")

        val hasNode: Boolean
        if (node == null) {
            hasNode = false
            node = Parser.parseNode(JsoupObjects.child(fade, ".node"))
        } else {
            hasNode = true
        }
        topicBuilder.setNode(node)

        val index = if (hasNode) 0 else 1
        if (fade.textNodes().size > index) {
            parseReplyTime(topicBuilder, fade.textNodes()[index])
        } else {
            // reply time may not exists
            topicBuilder.setReplyTime("")
        }
    }

    private fun parseReplyTime(topicBuilder: Topic.Builder, textNode: TextNode) {
        val text = textNode.text()
        val matcher = PATTERN_REPLY_TIME.matcher(text)
        if (!matcher.find()) {
            throw FatalException("match reply time for topic failed: " + text)
        }
        val time = matcher.group(1)
        topicBuilder.setReplyTime(time)
    }

    private fun parseTitle(topicBuilder: Topic.Builder, ele: Element) {
        val a = JsoupObjects(ele).child(".item_title").child("a").first()
        val url = a.attr("href")

        topicBuilder.setId(Topic.getIdFromUrl(url))
        topicBuilder.setTitle(a.html())
    }

    internal fun parseMember(builder: Topic.Builder, ele: Element) {
        var e = ele
        val memberBuilder = Member.Builder()

        // get member url
        e = e.child(0)
        Preconditions.checkState(e.tagName() == "a")
        val url = e.attr("href")
        memberBuilder.setUsername(Member.getNameFromUrl(url))

        // get member avatar
        val avatarBuilder = Avatar.Builder()
        e = e.child(0)
        Preconditions.checkState(e.tagName() == "img")
        avatarBuilder.setUrl(e.attr("src"))
        memberBuilder.setAvatar(avatarBuilder.createAvatar())

        builder.setMember(memberBuilder.createMember())
    }
}
