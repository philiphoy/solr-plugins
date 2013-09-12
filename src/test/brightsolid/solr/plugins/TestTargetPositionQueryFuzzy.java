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

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Tests for {@link SpanTargetPositionQuery}.
 */
public class TestTargetPositionQueryFuzzy extends LuceneTestCase {
  private Directory directory;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    directory = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    FieldType newType = new FieldType(org.apache.lucene.document.TextField.TYPE_STORED);
    newType.setOmitNorms(true);
    Field field = newField("field", "", newType);
    field.fieldType().setOmitNorms(true);

    doc.add(field);

    field.setStringValue("one two threx");
    iw.addDocument(doc);
    field.setStringValue("two threx one");
    iw.addDocument(doc);
    field.setStringValue("threx one twp");
    iw.addDocument(doc);

    reader = iw.getReader();
    iw.close();
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    directory.close();
    super.tearDown();
  }

  public void testTargetPositionFuzzy() throws Exception {
    FuzzyQuery fq = new FuzzyQuery(new Term("field", "three"));
    SpanQuery stq = new SpanMultiTermQueryWrapper<FuzzyQuery>(fq);
    SpanQuery tpq = new SpanTargetPositionQuery(stq, 1);
    TopDocs td = searcher.search(tpq, 10);

    assertEquals(fieldValue(td, 0), "two threx one");
    assertEquals(3, td.totalHits);
  }

  public void testTargetPositionPrefix() throws Exception {
    PrefixQuery fq = new PrefixQuery(new Term("field", "tw"));
    SpanQuery stq = new SpanMultiTermQueryWrapper<PrefixQuery>(fq);
    SpanQuery tpq = new SpanTargetPositionQuery(stq, 2);
    TopDocs td = searcher.search(tpq, 10);

    assertEquals(fieldValue(td, 0), "threx one twp");
    assertEquals(3, td.totalHits);
  }

  public void testTargetPositionWildcard() throws Exception {
    WildcardQuery fq = new WildcardQuery(new Term("field", "tw*"));
    SpanQuery stq = new SpanMultiTermQueryWrapper<WildcardQuery>(fq);
    SpanQuery tpq = new SpanTargetPositionQuery(stq, 2);
    TopDocs td = searcher.search(tpq, 10);

    assertEquals(fieldValue(td, 0), "threx one twp");
    assertEquals(3, td.totalHits);
  }

  private String fieldValue(TopDocs td, int i) throws IOException {
    return searcher.doc(td.scoreDocs[i].doc).getField("field").stringValue();
  }

}
