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
import scala.collection._

/** Implementation of the patience algorithm [1] to compute the longest common subsequence
 *
 *  [1] http://alfedenzo.livejournal.com/170301.html
 *
 *  @param withFallback whether to fallback to classic LCS when patience could not find the LCS
 *  @author Lucas Satabin
 */
class Patience[T](withFallback: Boolean = true) extends Lcs[T] {

  // algorithm we fall back to when patience algorithm is unable to find the LCS
  private val classicLcs: Option[Lcs[T]] =
    if (withFallback) Some(new MyersLcs[T]) else None

  /** An occurrence of a value associated to its index */
  type Occurrence = (T, Int)

  /** Returns occurrences that appear only once in the list, associated with their index */
  private def uniques(l: SeqView[T, IndexedSeq[T]]): List[Occurrence] =
    l.zipWithIndex.foldLeft(Map.empty[T, Int]) {
      case (acc, (value, idx)) =>
        // if not unique, remove it from the accumulator and go further
        if (acc.contains(value)) acc - value else acc.updated(value, idx)
    }.toList

  /** Takes all occurences from the first sequence and order them as in the second sequence if it is present */
  private def common(l1: List[Occurrence], l2: List[Occurrence]): List[(Occurrence, Int)] = {
    val l2Indices: Map[T, Int] = l2.map { case (t, idx) => t -> idx }(breakOut)
    l1.foldLeft(List.empty[(Occurrence, Int)]) {
      case (acc, occ @ (t, _)) =>
        l2Indices.get(t).fold(acc)(idx => (occ -> idx) :: acc)
    }.sortBy(_._2)
  }

  /** Returns the list of elements that appear only once in both l1 and l2 ordered as they appear in l2 with their index in l1 */
  private def uniqueCommons(seq1: SeqView[T, IndexedSeq[T]], seq2: SeqView[T, IndexedSeq[T]]): List[(Occurrence, Int)] = {
    // the values that occur only once in the first sequence
    val uniques1 = uniques(seq1)
    // the values that occur only once in the second sequence
    val uniques2 = uniques(seq2)
    // now order the unique occurrences as they appear in the second list
    common(uniques1, uniques2)
  }

  /** Returns the longest sequence */
  private def longest(l: List[(Occurrence, Int)]): List[Common] = {
    if (l.isEmpty) {
      Nil
    } else {
      type Stacked = List[(Int, Int)]

      def sort(l: List[(Occurrence, Int)]): List[List[Stacked]] =
        l.foldLeft(List[List[Stacked]]()) {
          case (acc, ((_, idx1), idx2)) =>

            @tailrec
            def push(stacks: List[List[Stacked]], last: List[(Int, Int)], acc: List[List[Stacked]]): List[List[Stacked]] =
              stacks match {
                case (stack @ (((idx, _) :: _) :: _)) :: tl if idx > idx1 =>
                  // we found the right stack
                  acc.reverse ::: (((idx1, idx2) :: last) :: stack) :: tl
                case (stack @ (stacked :: _)) :: tl =>
                  // try the next one
                  push(tl, stacked, stack :: acc)
                case Nil =>
                  // no stack corresponds, create a new one
                  acc.reverse ::: List(List((idx1, idx2) :: last))
              }

            push(acc, Nil, Nil)
        }

      val sorted = sort(l)
      // this call is safe as we know that the list of occurrence is not empty here and that there are no empty stacks
      val greatest = sorted.last.head

      greatest.foldRight(List.empty[Common]) {
        case ((idx1, idx2), next) => push(idx1, idx2, next, back = false)
      }.reverse // make the lcs in increasing order
    }
  }

  /** Computes the longest common subsequence between both sequences.
   *  It is encoded as the list of common indices in the first and the second sequence.
   */
  def lcsInner(seq1: IndexedSeq[T], glow1: Int, seq2: IndexedSeq[T], glow2: Int): List[Common] = {
    // fill the holes with possibly common (not unique) elements
    def loop(low1: Int, low2: Int, high1: Int, high2: Int, acc: List[Common]): List[Common] =
      if (low1 >= high1 || low2 >= high2) {
        acc
      } else {
        var lastPos1 = low1 - 1
        var lastPos2 = low2 - 1
        var answer = acc
        for (Common(p1, p2, l) <- longest(uniqueCommons(seq1.view(low1, high1), seq2.view(low2, high2)))) {
          // recurse between lines which are unique in each sequence
          val pos1 = p1 + low1
          val pos2 = p2 + low2
          // most of the time we have sequences of similar entries
          if (lastPos1 + 1 != pos1 || lastPos2 + 1 != pos2)
            answer = loop(lastPos1 + 1, lastPos2 + 1, pos1, pos2, answer)
          lastPos1 = pos1
          lastPos2 = pos2
          answer = push(Common(pos1 + glow1, pos2 + glow2, l), answer, false)
        }
        if (answer != acc) {
          // the size of the accumulator increased, find
          // matches between the last match and the end
          loop(lastPos1 + 1, lastPos2 + 1, high1, high2, answer)
        } else if (seq1(low1) == seq2(low2)) {
          // find lines that match at the beginning
          var newLow1 = low1
          var newLow2 = low2
          while (newLow1 < high1 && newLow2 < high2 && seq1(newLow1) == seq2(newLow2)) {
            answer = push(newLow1 + glow1, newLow2 + glow2, answer, false)
            newLow1 += 1
            newLow2 += 1
          }
          loop(newLow1, newLow2, high1, high2, answer)
        } else if (seq1(high1 - 1) == seq2(high2 - 1)) {
          // find lines that match at the end
          var newHigh1 = high1 - 1
          var newHigh2 = high2 - 1
          while (newHigh1 > low1 && newHigh2 > low2 && seq1(newHigh1 - 1) == seq2(newHigh2 - 1)) {
            newHigh1 -= 1
            newHigh2 -= 1
          }
          answer = loop(lastPos1 + 1, lastPos2 + 1, newHigh1, newHigh2, answer)
          for (i <- 0 until (high1 - newHigh1))
            answer = push(newHigh1 + i + glow1, newHigh2 + i + glow2, answer, false)
          answer
        } else {
          classicLcs match {
            case Some(classicLcs) =>
              // fall back to classic LCS algorithm when there is no unique common elements
              // between both sequences and they have no common prefix nor suffix
              // raw patience algorithm is not good for finding LCS in such cases
              classicLcs.lcs(seq1.slice(low1, high1), seq2.slice(low2, high2)).foldLeft(answer) { (acc, common) =>
                push(Common(common.start1 + glow1, common.start2 + glow2, common.length), acc, false)
              }

            case _ =>
              answer
          }
        }

      }
    // we start with first indices in both sequences
    loop(0, 0, seq1.size, seq2.size, Nil)
  }

}
