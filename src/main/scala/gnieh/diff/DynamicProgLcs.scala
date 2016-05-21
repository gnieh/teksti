/*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.diff

import scala.annotation.tailrec

/** Implementation of the LCS using dynamic programming.
 *
 *  @author Lucas Satabin
 */
class DynamicProgLcs[T] extends Lcs[T] {

  def lcs(s1: IndexedSeq[T], s2: IndexedSeq[T], low1: Int, high1: Int, low2: Int, high2: Int): List[(Int, Int)] = {
    val seq1 = s1.slice(low1, high1)
    val seq2 = s2.slice(low2, high2)
    if (seq1.isEmpty || seq2.isEmpty) {
      // shortcut if at least on sequence is empty, the lcs, is empty as well
      Nil
    } else if (seq1 == seq2) {
      // both sequences are equal, the lcs is either of them
      seq1.indices.map(i => (i + low1, i + low2)).toList
    } else if (seq1.startsWith(seq2)) {
      // the second sequence is a prefix of the first one
      // the lcs is the second sequence
      seq2.indices.map(i => (i + low1, i + low2)).toList
    } else if (seq2.startsWith(seq1)) {
      // the first sequence is a prefix of the second one
      // the lcs is the first sequence
      seq1.indices.map(i => (i + low1, i + low2)).toList
    } else {
      // we try to reduce the problem by stripping common suffix and prefix
      val (prefix, middle1, middle2, suffix) = splitPrefixSuffix(seq1, seq2, low1, low2)
      val offset = prefix.size
      val lengths = Array.ofDim[Int](middle1.size + 1, middle2.size + 1)
      // fill up the length matrix
      for {
        i <- 0 until middle1.size
        j <- 0 until middle2.size
      } if (middle1(i) == middle2(j))
        lengths(i + 1)(j + 1) = lengths(i)(j) + 1
      else
        lengths(i + 1)(j + 1) = math.max(lengths(i + 1)(j), lengths(i)(j + 1))
      // and compute the lcs out of the matrix
      @tailrec
      def loop(idx1: Int, idx2: Int, acc: List[(Int, Int)]): List[(Int, Int)] =
        if (idx1 == 0 || idx2 == 0) {
          acc
        } else if (lengths(idx1)(idx2) == lengths(idx1 - 1)(idx2)) {
          loop(idx1 - 1, idx2, acc)
        } else if (lengths(idx1)(idx2) == lengths(idx1)(idx2 - 1)) {
          loop(idx1, idx2 - 1, acc)
        } else {
          loop(idx1 - 1, idx2 - 1, (low1 + offset + idx1 - 1, low2 + offset + idx2 - 1) :: acc)
        }

      prefix ++ loop(middle1.size, middle2.size, Nil) ++ suffix
    }
  }

  /* Extract common prefix and suffix from both sequences */
  private def splitPrefixSuffix(seq1: IndexedSeq[T], seq2: IndexedSeq[T], low1: Int, low2: Int): (List[(Int, Int)], IndexedSeq[T], IndexedSeq[T], List[(Int, Int)]) = {
    val size1 = seq1.size
    val size2 = seq2.size
    val size = math.min(size1, size2)
    @tailrec
    def prefixLoop(idx: Int, acc: List[(Int, Int)]): List[(Int, Int)] =
      if (idx >= size || seq1(idx) != seq2(idx)) {
        acc.reverse
      } else {
        prefixLoop(idx + 1, (idx + low1, idx + low2) :: acc)
      }
    val prefix = prefixLoop(0, Nil)
    @tailrec
    def suffixLoop(idx1: Int, idx2: Int, acc: List[(Int, Int)]): List[(Int, Int)] =
      if (idx1 < 0 || idx2 < 0 || seq1(idx1) != seq2(idx2)) {
        acc.reverse
      } else {
        suffixLoop(idx1 - 1, idx2 - 1, (idx1, idx2) :: acc)
      }
    val suffix = suffixLoop(size1 - 1, size2 - 1, Nil)
    (prefix, seq1.drop(prefix.size).dropRight(suffix.size), seq2.drop(prefix.size).dropRight(suffix.size), suffix)
  }

}
