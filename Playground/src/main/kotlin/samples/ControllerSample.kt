package org.genhttp.playground.samples

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.controllers.ControllerAction
import org.genhttp.modules.controllers.FromPath
import org.genhttp.modules.controllers.addController
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.Method

object ControllerSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Shows how to declare and register a controller that will be invoked
         * to handle incoming HTTP requests.
         *
         * See https://genhttp-kt.genhttp.run/documentation/content/frameworks/controllers/
         *
         */
        return Layout.create()
            .addController<BookController>("books")
    }

    data class Book(val id: Int, val title: String)

    class BookController {

        // GET http://localhost:8080/books/
        @ControllerAction
        fun index(): List<Book> = Books

        // PUT http://localhost:8080/books/create with JSON/XML/... payload
        @ControllerAction(Method.Put)
        fun create(book: Book): Book {
            val toAdd = book.copy(id = Books.maxOf { it.id } + 1)
            Books.add(toAdd)
            return toAdd
        }

        // DELETE http://localhost:8080/books/delete/1
        @ControllerAction(Method.Delete)
        fun delete(@FromPath id: Int) {
            Books.removeAll { it.id == id }
        }

        private companion object {
            val Books = mutableListOf(Book(1, "Lord of the Rings"))
        }

    }

}
