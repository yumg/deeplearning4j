package org.deeplearning4j.models.sequencevectors.transformers.impl.iterables;

import lombok.extern.slf4j.Slf4j;
import org.datavec.api.util.ClassPathResource;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.MutipleEpochsSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.PrefetchingSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author raver119@gmail.com
 */
@Slf4j
public class ParallelTransformerIteratorTest {
    private TokenizerFactory factory = new DefaultTokenizerFactory();

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void hasNext() throws Exception {
        SentenceIterator iterator = new PrefetchingSentenceIterator.Builder(new BasicLineIterator(new ClassPathResource("/big/raw_sentences.txt").getFile()))
                .setFetchSize(1000)
                .build();

        SentenceTransformer transformer = new SentenceTransformer.Builder()
                .iterator(iterator)
                .allowMultithreading(true)
                .tokenizerFactory(factory)
                .build();

        Iterator<Sequence<VocabWord>> iter = transformer.iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            Sequence<VocabWord> sequence = iter.next();
            assertNotEquals("Failed on [" + cnt + "] iteration", null, sequence);
            assertNotEquals("Failed on [" + cnt + "] iteration", 0, sequence.size());
            cnt++;
        }

        assertEquals(97162, cnt);
    }

    @Test
    public void testSpeedComparison1() throws Exception {
        SentenceIterator iterator = new MutipleEpochsSentenceIterator(new BasicLineIterator(new ClassPathResource("/big/raw_sentences.txt").getFile()), 25);

        SentenceTransformer transformer = new SentenceTransformer.Builder()
                .iterator(iterator)
                .allowMultithreading(false)
                .tokenizerFactory(factory)
                .build();

        Iterator<Sequence<VocabWord>> iter = transformer.iterator();
        int cnt = 0;
        long time1 = System.currentTimeMillis();
        while (iter.hasNext()) {
            Sequence<VocabWord> sequence = iter.next();
            assertNotEquals("Failed on [" + cnt + "] iteration", null, sequence);
            assertNotEquals("Failed on [" + cnt + "] iteration", 0, sequence.size());
            cnt++;
        }
        long time2 = System.currentTimeMillis();

        log.info("Single-threaded time: {} ms", time2 - time1);
        iterator.reset();

        transformer = new SentenceTransformer.Builder()
                .iterator(iterator)
                .allowMultithreading(true)
                .tokenizerFactory(factory)
                .build();

        iter = transformer.iterator();

        time1 = System.currentTimeMillis();
        while (iter.hasNext()) {
            Sequence<VocabWord> sequence = iter.next();
            assertNotEquals("Failed on [" + cnt + "] iteration", null, sequence);
            assertNotEquals("Failed on [" + cnt + "] iteration", 0, sequence.size());
            cnt++;
        }
        time2 = System.currentTimeMillis();

        log.info("Multi-threaded time: {} ms", time2 - time1);


        SentenceIterator baseIterator = iterator;
        baseIterator.reset();

        iterator = new PrefetchingSentenceIterator.Builder(baseIterator)
                .setFetchSize(1024)
                .build();


        iter = transformer.iterator();
        time1 = System.currentTimeMillis();
        while (iter.hasNext()) {
            Sequence<VocabWord> sequence = iter.next();
            assertNotEquals("Failed on [" + cnt + "] iteration", null, sequence);
            assertNotEquals("Failed on [" + cnt + "] iteration", 0, sequence.size());
            cnt++;
        }
        time2 = System.currentTimeMillis();

        log.info("Prefetched Single-threaded time: {} ms", time2 - time1);
        iterator.reset();

        transformer = new SentenceTransformer.Builder()
                .iterator(iterator)
                .allowMultithreading(true)
                .tokenizerFactory(factory)
                .build();

        iter = transformer.iterator();

        time1 = System.currentTimeMillis();
        while (iter.hasNext()) {
            Sequence<VocabWord> sequence = iter.next();
            assertNotEquals("Failed on [" + cnt + "] iteration", null, sequence);
            assertNotEquals("Failed on [" + cnt + "] iteration", 0, sequence.size());
            cnt++;
        }
        time2 = System.currentTimeMillis();

        log.info("Prefetched Multi-threaded time: {} ms", time2 - time1);

    }

}