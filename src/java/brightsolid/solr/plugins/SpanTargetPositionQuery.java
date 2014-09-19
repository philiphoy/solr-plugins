package brightsolid.solr.plugins;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanScorer;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

import org.apache.lucene.util.ToStringUtils;

import java.io.IOException;

import java.util.Map;
import java.util.Set;

/**
 ** Class for scoring SpanQuery based on the target position of a match.
 **/
public class SpanTargetPositionQuery extends SpanQuery implements Cloneable {
  protected SpanQuery match;
  protected int target;

  public SpanTargetPositionQuery(SpanQuery match, int target) {
    this.match = match;
    this.target = target;
  }
 
  /**
   * @return the SpanQuery whose matches are filtered.
   * */
  public SpanQuery getMatch() {
    return match;
  }

  @Override
  public String getField() {
    return match.getField();
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    match.extractTerms(terms);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    SpanTargetPositionQuery clone = null;

    SpanQuery rewritten = (SpanQuery) match.rewrite(reader);
    if (rewritten != match) {
      clone = (SpanTargetPositionQuery) this.clone();
      clone.match = rewritten;
    }

    if (clone != null) {
      return clone; // some clauses rewrote
    } else {
      return this; // no clauses rewrote
    }
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("spanTargPos(");
    buffer.append(match.toString(field));
    buffer.append(",");
    buffer.append(target);
    buffer.append(")");
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public SpanTargetPositionQuery clone() {
    SpanTargetPositionQuery result = new SpanTargetPositionQuery((SpanQuery) match.clone(), target);
    result.setBoost(getBoost());
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof SpanTargetPositionQuery))
      return false;

    SpanTargetPositionQuery other = (SpanTargetPositionQuery) o;
    return this.target == other.target && this.match.equals(other.match) && this.getBoost() == other.getBoost();
  }

  @Override
  public int hashCode() {
    int h = match.hashCode();
    h ^= (h << 8) | (h >>> 25); // reversible
    h ^= Float.floatToRawIntBits(getBoost()) ^ target;
    return h;
  }

  @Override
  public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term, TermContext> termContexts)
          throws IOException {
    return match.getSpans(context, acceptDocs, termContexts);
  }

  @Override
  public Weight createWeight(IndexSearcher searcher) throws IOException {
    return new TargetPositionWeight(this, searcher, target);
  }

  protected class TargetPositionWeight extends SpanWeight {
    private int target;

    public TargetPositionWeight(SpanTargetPositionQuery query, IndexSearcher searcher, int target) throws IOException {
      super(query, searcher);
      this.target = target;
    }

    @Override
    public Scorer scorer(AtomicReaderContext context, boolean scoreDocsInOrder, boolean topScorer, Bits acceptDocs)
            throws IOException {
      if (stats == null) {
        return null;
      } else {
        return new TargetPositionScorer(query.getSpans(context, acceptDocs, termContexts), this, similarity.simScorer( 
              stats, context));
      }
    }

    protected class TargetPositionScorer extends SpanScorer {

      public TargetPositionScorer(Spans spans, Weight weight,  Similarity.SimScorer docScorer) throws IOException {
        super(spans, weight, docScorer);
      }

     
      @Override
      protected boolean setFreqCurrentDoc() throws IOException {
        if (!more) {
          return false;
        }
        doc = spans.doc();
        freq = 0.0f;
        numMatches = 0;
        do {
          int matchLength = Math.abs(spans.start() - target);
          freq += docScorer.computeSlopFactor(matchLength);
          numMatches++;
          more = spans.next();
        } while (more && (doc == spans.doc()));
        return true;
      }
    }
  }
}
