package org.genhttp.playground.samples

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.functional.*
import org.genhttp.modules.layouting.Layout

object FunctionalSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Shows how to use lambdas that will be invoked
         * to handle incoming HTTP requests.
         *
         * See https://genhttp.org/documentation/content/frameworks/functional/
         *
         */
        val books = mutableListOf(Book(1, "Lord of the Rings"))

        val service = Inline.create()
            .get { books } // GET http://localhost:8080/books/
            .put { book: Book -> // PUT http://localhost:8080/books/ with JSON/XML/... payload
                val toAdd = book.copy(id = books.maxOf { it.id } + 1)
                books.add(toAdd)
                toAdd
            }
            .delete(":id") { id: Int -> books.removeAll { it.id == id } } // DELETE http://localhost:8080/books/1

        return Layout.create()
            .add("books", service)
    }

    data class Book(val id: Int, val title: String)

}
