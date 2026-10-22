package org.genhttp.playground.samples

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.Method
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService

object WebserviceSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Shows how to declare and register a class that will be invoked
         * to handle incoming HTTP requests.
         *
         * See https://genhttp-kt.genhttp.run/documentation/content/frameworks/webservices/
         *
         */
        return Layout.create()
            .addService<BookService>("books")
    }

    data class Book(val id: Int, val title: String)

    class BookService {

        // GET http://localhost:8080/books/
        @ResourceMethod
        fun list(): List<Book> = Books

        // PUT http://localhost:8080/books/ with JSON/XML/... payload
        @ResourceMethod(Method.Put)
        fun create(book: Book): Book {
            val toAdd = book.copy(id = Books.maxOf { it.id } + 1)
            Books.add(toAdd)
            return toAdd
        }

        // DELETE http://localhost:8080/books/1
        @ResourceMethod(Method.Delete, ":id")
        fun delete(id: Int) {
            Books.removeAll { it.id == id }
        }

        private companion object {
            val Books = mutableListOf(Book(1, "Lord of the Rings"))
        }

    }

}
