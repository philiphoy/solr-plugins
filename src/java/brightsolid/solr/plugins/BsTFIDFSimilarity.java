package brightsolid.solr.plugins;

import java.io.IOException;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.Similarity.SimScorer;
import org.apache.lucene.search.similarities.Similarity.SimWeight;

import org.apache.lucene.util.BytesRef;

public abstract class BsTFIDFSimilarity  extends Similarity {
		  
  /**
   * Sole constructor. (For invocation by subclass 
   * constructors, typically implicit.)
   */
  public BsTFIDFSimilarity() {}
  
  /** Computes a score factor based on the fraction of all query terms that a
   * document contains.  This value is multiplied into scores.
   *
   * <p>The presence of a large portion of the query terms indicates a better
   * match with the query, so implementations of this method usually return
   * larger values when the ratio between these parameters is large and smaller
   * values when the ratio between them is small.
   *
   * @param overlap the number of query terms matched in the document
   * @param maxOverlap the total number of terms in the query
   * @return a score factor based on term overlap with the query
   */
  @Override
  public abstract float coord(int overlap, int maxOverlap);
  
  /** Computes the normalization value for a query given the sum of the squared
   * weights of each of the query terms.  This value is multiplied into the
   * weight of each query term. While the classic query normalization factor is
   * computed as 1/sqrt(sumOfSquaredWeights), other implementations might
   * completely ignore sumOfSquaredWeights (ie return 1).
   *
   * <p>This does not affect ranking, but the default implementation does make scores
   * from different queries more comparable than they would be by eliminating the
   * magnitude of the Query vector as a factor in the score.
   *
   * @param sumOfSquaredWeights the sum of the squares of query term weights
   * @return a normalization factor for query weights
   */
  @Override
  public abstract float queryNorm(float sumOfSquaredWeights);
  
  /** Computes a score factor based on a term or phrase's frequency in a
   * document.  This value is multiplied by the {@link #idf(long, long)}
   * factor for each term in the query and these products are then summed to
   * form the initial score for a document.
   *
   * <p>Terms and phrases repeated in a document indicate the topic of the
   * document, so implementations of this method usually return larger values
   * when <code>freq</code> is large, and smaller values when <code>freq</code>
   * is small.
   *
   * @param freq the frequency of a term within a document
   * @return a score factor based on a term's within-document frequency
   */
  public abstract float tf(float freq);

  /**
   * Computes a score factor for a simple term and returns an explanation
   * for that score factor.
   * 
   * <p>
   * The default implementation uses:
   * 
   * <pre class="prettyprint">
   * idf(docFreq, searcher.maxDoc());
   * </pre>
   * 
   * Note that {@link CollectionStatistics#maxDoc()} is used instead of
   * {@link org.apache.lucene.index.IndexReader#numDocs() IndexReader#numDocs()} because also 
   * {@link TermStatistics#docFreq()} is used, and when the latter 
   * is inaccurate, so is {@link CollectionStatistics#maxDoc()}, and in the same direction.
   * In addition, {@link CollectionStatistics#maxDoc()} is more efficient to compute
   *   
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the term
   * @return an Explain object that includes both an idf score factor 
             and an explanation for the term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long max = collectionStats.maxDoc();
    final float idf = idf(df, max);
    return new Explanation(idf, "idf(docFreq=" + df + ", maxDocs=" + max + ")");
  }

  /**
   * Computes a score factor for a phrase.
   * 
   * <p>
   * The default implementation sums the idf factor for
   * each term in the phrase.
   * 
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the terms in the phrase
   * @return an Explain object that includes both an idf 
   *         score factor for the phrase and an explanation 
   *         for each term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    final long max = collectionStats.maxDoc();
    float idf = 0.0f;
    final Explanation exp = new Explanation();
    exp.setDescription("idf(), sum of:");
    for (final TermStatistics stat : termStats ) {
      final long df = stat.docFreq();
      final float termIdf = idf(df, max);
      exp.addDetail(new Explanation(termIdf, "idf(docFreq=" + df + ", maxDocs=" + max + ")"));
      idf += termIdf;
    }
    exp.setValue(idf);
    return exp;
  }

  /** Computes a score factor based on a term's document frequency (the number
   * of documents which contain the term).  This value is multiplied by the
   * {@link #tf(float)} factor for each term in the query and these products are
   * then summed to form the initial score for a document.
   *
   * <p>Terms that occur in fewer documents are better indicators of topic, so
   * implementations of this method usually return larger values for rare terms,
   * and smaller values for common terms.
   *
   * @param docFreq the number of documents which contain the term
   * @param numDocs the total number of documents in the collection
   * @return a score factor based on the term's document frequency
   */
  public abstract float idf(long docFreq, long numDocs);

  /**
   * Compute an index-time normalization value for this field instance.
   * <p>
   * This value will be stored in a single byte lossy representation by 
   * {@link #encodeNormValue(float)}.
   * 
   * @param state statistics of the current field (such as length, boost, etc)
   * @return an index-time normalization value
   */
  public abstract float lengthNorm(FieldInvertState state);
  
  @Override
  public final long computeNorm(FieldInvertState state) {
    float normValue = lengthNorm(state);
    return encodeNormValue(normValue);
  }
  
  /**
   * Decodes a normalization factor stored in an index.
   * 
   * @see #encodeNormValue(float)
   */
  public abstract float decodeNormValue(long norm);

  /** Encodes a normalization factor for storage in an index. */
  public abstract long encodeNormValue(float f);
 
  /** Computes the amount of a sloppy phrase match, based on an edit distance.
   * This value is summed for each sloppy phrase match in a document to form
   * the frequency to be used in scoring instead of the exact term count.
   *
   * <p>A phrase match with a small edit distance to a document passage more
   * closely matches the document, so implementations of this method usually
   * return larger values when the edit distance is small and smaller values
   * when it is large.
   *
   * @see PhraseQuery#setSlop(int)
   * @param distance the edit distance of this sloppy phrase match
   * @return the frequency increment for this match
   */
  public abstract float sloppyFreq(int distance);

  /**
   * Calculate a scoring factor based on the data in the payload.  Implementations
   * are responsible for interpreting what is in the payload.  Lucene makes no assumptions about
   * what is in the byte array.
   *
   * @param doc The docId currently being scored.
   * @param start The start position of the payload
   * @param end The end position of the payload
   * @param payload The payload byte array to be scored
   * @return An implementation dependent float to be used as a scoring factor
   */
  public abstract float scorePayload(int doc, int start, int end, BytesRef payload);

  @Override
  public final SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    final Explanation idf = termStats.length == 1
    ? idfExplain(collectionStats, termStats[0])
    : idfExplain(collectionStats, termStats);
    return new IDFStats(collectionStats.field(), idf, queryBoost);
  }

  @Override
  public SimScorer simScorer(SimWeight stats, AtomicReaderContext context) throws IOException {
    IDFStats idfstats = (IDFStats) stats;
    return new TFIDFSimScorer(idfstats, context.reader().getNormValues(idfstats.field));
  }
  
   final class TFIDFSimScorer extends SimScorer {
    private final IDFStats stats;
    private final float weightValue;
    private final NumericDocValues norms;
    
    TFIDFSimScorer(IDFStats stats, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.weightValue = stats.value;
      this.norms = norms;
    }
    
    @Override
    public float score(int doc, float freq) {
      final float raw = tf(freq) * weightValue; // compute tf(f)*weight
      
      return norms == null ? raw : raw * decodeNormValue(norms.get(doc));  // normalize for field
    }
    
    @Override
    public float computeSlopFactor(int distance) {
      return sloppyFreq(distance);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return scorePayload(doc, start, end, payload);
    }

    @Override
    public Explanation explain(int doc, Explanation freq) {
      return explainScore(doc, freq, stats, norms);
    }
  }
  
  /** Collection statistics for the TF-IDF model. The only statistic of interest
   * to this model is idf. */
  static class IDFStats extends SimWeight {
    final String field;
    /** The idf and its explanation */
    private final Explanation idf;
    private float queryNorm;
    private float queryWeight;
    private final float queryBoost;
    private float value;
    
    public IDFStats(String field, Explanation idf, float queryBoost) {
      // TODO: Validate?
      this.field = field;
      this.idf = idf;
      this.queryBoost = queryBoost;
      this.queryWeight = idf.getValue() * queryBoost; // compute query weight
    }

    @Override
    public float getValueForNormalization() {
      // TODO: (sorta LUCENE-1907) make non-static class and expose this squaring via a nice method to subclasses?
      return queryWeight * queryWeight;  // sum of squared weights
    }

    @Override
    public void normalize(float queryNorm, float topLevelBoost) {
      this.queryNorm = queryNorm * topLevelBoost;
      queryWeight *= this.queryNorm;              // normalize query weight
      value = queryWeight * idf.getValue();         // idf for document
    }
  }  

  private Explanation explainScore(int doc, Explanation freq, IDFStats stats, NumericDocValues norms) {
    Explanation result = new Explanation();
    result.setDescription("score(doc="+doc+",freq="+freq+"), product of:");

    // explain query weight
    Explanation queryExpl = new Explanation();
    queryExpl.setDescription("queryWeight, product of:");

    Explanation boostExpl = new Explanation(stats.queryBoost, "boost");
    if (stats.queryBoost != 1.0f)
      queryExpl.addDetail(boostExpl);
    queryExpl.addDetail(stats.idf);

    Explanation queryNormExpl = new Explanation(stats.queryNorm,"queryNorm");
    queryExpl.addDetail(queryNormExpl);

    queryExpl.setValue(boostExpl.getValue() *
                       stats.idf.getValue() *
                       queryNormExpl.getValue());

    result.addDetail(queryExpl);

    // explain field weight
    Explanation fieldExpl = new Explanation();
    fieldExpl.setDescription("fieldWeight in "+doc+
                             ", product of:");

    Explanation tfExplanation = new Explanation();
    tfExplanation.setValue(tf(freq.getValue()));
    tfExplanation.setDescription("tf(freq="+freq.getValue()+"), with freq of:");
    tfExplanation.addDetail(freq);
    fieldExpl.addDetail(tfExplanation);
    fieldExpl.addDetail(stats.idf);

    Explanation fieldNormExpl = new Explanation();
    float fieldNorm = norms != null ? decodeNormValue(norms.get(doc)) : 1.0f;
    fieldNormExpl.setValue(fieldNorm);
    fieldNormExpl.setDescription("fieldNorm(doc="+doc+")");
    fieldExpl.addDetail(fieldNormExpl);
    
    fieldExpl.setValue(tfExplanation.getValue() *
                       stats.idf.getValue() *
                       fieldNormExpl.getValue());

    result.addDetail(fieldExpl);
    
    // combine them
    result.setValue(queryExpl.getValue() * fieldExpl.getValue());

    if (queryExpl.getValue() == 1.0f)
      return fieldExpl;

    return result;
  }
}
