/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import bindgen_tests.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class RecursiveTypesTest {
    // Tree (self-recursive)
    @Test
    fun testTree() {
        val leaf = Tree.Leaf(5)
        sumTree(leaf) shouldBe 5

        val node = Tree.Node(Tree.Leaf(3), Tree.Leaf(4))
        sumTree(node) shouldBe 7

        val deep = Tree.Node(
            Tree.Node(Tree.Leaf(1), Tree.Leaf(2)),
            Tree.Leaf(10),
        )
        sumTree(deep) shouldBe 13
    }

    // Expr / BoolExpr (mutually recursive)
    @Test
    fun testExpr() {
        evalExpr(Expr.Lit(42)) shouldBe 42

        // Rustine escapes the C++ keywords in names, so the `else_` field keeps its underscore,
        // where upstream names it `else`.
        val ifTrue = Expr.If(
            cond = BoolExpr.IsZero(Expr.Lit(0)),
            then = Expr.Lit(1),
            else_ = Expr.Lit(2),
        )
        evalExpr(ifTrue) shouldBe 1

        val ifFalse = Expr.If(
            cond = BoolExpr.IsZero(Expr.Lit(5)),
            then = Expr.Lit(1),
            else_ = Expr.Lit(2),
        )
        evalExpr(ifFalse) shouldBe 2

        evalBool(BoolExpr.Not(BoolExpr.True)) shouldBe false
        evalBool(BoolExpr.Not(BoolExpr.False)) shouldBe true
    }

    // LinkedList (cycle through Optional: Cons.tail is LinkedList?)
    @Test
    fun testLinkedList() {
        listSum(LinkedList.Nil) shouldBe 0
        listSum(LinkedList.Cons(head = 5, tail = null)) shouldBe 5
        listSum(LinkedList.Cons(head = 3, tail = LinkedList.Cons(head = 4, tail = null))) shouldBe 7
    }

    // Trie (cycle through Map value: Branch.children is Map<String, Trie>)
    @Test
    fun testTrie() {
        trieSum(Trie.Leaf(5)) shouldBe 5
        trieSum(Trie.Branch(children = mapOf())) shouldBe 0
        trieSum(Trie.Branch(children = mapOf("a" to Trie.Leaf(1), "b" to Trie.Leaf(2)))) shouldBe 3
        trieSum(
            Trie.Branch(children = mapOf("x" to Trie.Branch(children = mapOf("y" to Trie.Leaf(7)))))
        ) shouldBe 7
    }

    // RoseTree (cycle through a record: RoseTree → RoseData → List<RoseTree>)
    @Test
    fun testRoseTree() {
        sumRoseTree(RoseTree.Leaf(7)) shouldBe 7

        sumRoseTree(RoseTree.Branch(RoseData(value = 10, children = listOf()))) shouldBe 10

        sumRoseTree(
            RoseTree.Branch(
                RoseData(value = 1, children = listOf(RoseTree.Leaf(2), RoseTree.Leaf(3)))
            )
        ) shouldBe 6

        sumRoseTree(
            RoseTree.Branch(
                RoseData(
                    value = 1,
                    children = listOf(
                        RoseTree.Branch(RoseData(value = 2, children = listOf(RoseTree.Leaf(3)))),
                        RoseTree.Leaf(4),
                    ),
                )
            )
        ) shouldBe 10
    }

    // EvalError (recursive error enum)
    @Test
    fun testRecursiveError() {
        maybeThrowError(false) shouldBe 42
        // recursive error enum compiled and is throwable
        val e = shouldThrow<EvalException.Nested> {
            maybeThrowError(true)
        }
        // Upstream doesn't check the nested error in Kotlin.
        e.inner.shouldBeInstanceOf<EvalException.Overflow>()
    }
}
