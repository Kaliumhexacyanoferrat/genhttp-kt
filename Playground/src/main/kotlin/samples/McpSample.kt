package org.genhttp.playground.samples

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.documentation.Summary
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.Tools

import java.time.Instant

object McpSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Shows how to provide tools to AI agents such as Claude via the
         * Model Context Protocol (MCP).
         *
         * Add the endpoint to Claude Code via
         *
         *   claude mcp add --transport http library http://localhost:8080/mcp
         *
         * and ask, for example, "Which books about Rome are in the library?".
         *
         * See https://genhttp-kt.genhttp.run/documentation/content/frameworks/mcp/
         *
         */
        val tools = Tools.create()
            .server("library", "1.0.0", "Library")
            .instructions("Search for books before lending them to a reader.")
            .add<LibraryTools>()
            .add("get_time", "Returns the current time of the library (UTC)", ::getTime)

        return Layout.create()
            .add("mcp", tools)
    }

    @Tool(readOnly = [true])
    fun getTime(): Instant = Instant.now()

    data class Book(val id: Int, val title: String, val lentTo: String?)

    class LibraryTools {

        @Tool(readOnly = [true])
        @Summary("Searches the books of the library by title")
        fun findBooks(@Summary("Part of the title, e.g. 'Rome'") query: String, @Min(1) @Max(50) limit: Int = 10): List<Book> =
            Books.filter { it.title.contains(query, ignoreCase = true) }.take(limit)

        @Tool(destructive = [false])
        @Summary("Lends a book to a reader")
        fun lendBook(@Summary("The id of the book, as returned by find_books") bookId: Int, @Summary("The name of the reader") reader: String): Book {
            val book = Books.firstOrNull { it.id == bookId }
                ?: throw ProviderException(ResponseStatus.NotFound, "There is no book with id $bookId.")

            if (book.lentTo != null) {
                throw ProviderException(ResponseStatus.Conflict, "'${book.title}' is already lent to ${book.lentTo}.")
            }

            val lent = book.copy(lentTo = reader)

            Books[Books.indexOf(book)] = lent

            return lent
        }

        private companion object {

            val Books = mutableListOf(
                Book(1, "SPQR: A History of Ancient Rome", null),
                Book(2, "The Lord of the Rings", null)
            )

        }

    }

}
