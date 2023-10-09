@file:OptIn(ExperimentalForeignApi::class, ExperimentalSerializationApi::class)

import kotlinx.cinterop.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonNames
import platform.posix.*
import kotlin.time.measureTime

@Serializable
data class Post(@JsonNames("_id") val id: String, val title: String, val tags: List<String>)

@Serializable
data class RelatedPosts(val _id: String, val tags: List<String>, val related: Array<Post?>);

data class PostWithSharedTags(var count: Int, var postId: Int)

const val TOP_N = 5;

fun main() {
    val posts = Json.decodeFromString<Array<Post>>(readAllText("../posts.json"))

    val allRelatedPosts: Array<RelatedPosts?>?;
    val timeInMillis = measureTime {
        val tagMap = HashMap<String, ArrayList<Int>>(posts.size)
        for (i in posts.indices) {
            for (tag: String in posts[i].tags) {
                tagMap.getOrPut(tag) { ArrayList<Int>() }.add(i)
            }
        }

        allRelatedPosts = arrayOfNulls<RelatedPosts>(posts.count())
        val top5 = arrayOf(
            PostWithSharedTags(0, 0),
            PostWithSharedTags(0, 0),
            PostWithSharedTags(0, 0),
            PostWithSharedTags(0, 0),
            PostWithSharedTags(0, 0),
        )
        val topPosts = arrayOfNulls<Post>(TOP_N)
        val taggedPostCount = IntArray(posts.count()) { 0 }

        for (i in posts.indices) {
            val post = posts[i];

            for (tag in post.tags) {
                for (otherPostIdx in tagMap[tag]!!) {
                    taggedPostCount[otherPostIdx]++;
                }
            }

            taggedPostCount[i] = 0; // Don't count self

            var minTags = 0

            for (j in taggedPostCount.indices) {
                val count = taggedPostCount[j]

                if (count > minTags) {

                    var upperBound = TOP_N - 2;
                    while (upperBound >= 0 && count > top5[upperBound].count) {
                        top5[upperBound + 1] = top5[upperBound]
                        upperBound--
                    }

                    val p = top5[upperBound + 1]
                    p.count = count
                    p.postId = j

                    minTags = top5[TOP_N - 1].count
                }
            }

            for (j in 0..< TOP_N) {
                topPosts[j] = posts[top5[j].postId]
            }

            allRelatedPosts[i] = RelatedPosts(post.id, post.tags, topPosts)
        }
    }

    println("Processing time (w/o IO): ${timeInMillis.inWholeMilliseconds}ms");

    val result = Json.encodeToString(allRelatedPosts)
    writeAllText("../related_posts_kotlin_native.json", result)
}

fun readAllText(filename: String): String {
    val returnBuffer = StringBuilder()
    val file = fopen(filename, "r") ?:
    throw IllegalArgumentException("Cannot open file $filename")

    try {
        memScoped {
            val readBufferLength = 64 * 1024
            val buffer = allocArray<ByteVar>(readBufferLength)
            var line = fgets(buffer, readBufferLength, file)?.toKString()
            while (line != null) {
                returnBuffer.append(line)
                line = fgets(buffer, readBufferLength, file)?.toKString()
            }
        }
    } finally {
        fclose(file)
    }

    return returnBuffer.toString();
}

fun writeAllText(filename: String, text: String) {
    val file = fopen(filename, "w") ?:
    throw IllegalArgumentException("Cannot open file $filename")

    try {
        memScoped {
            if(fputs(text, file) == EOF) throw Error("File write error")
        }
    } finally {
        fclose(file)
    }
}