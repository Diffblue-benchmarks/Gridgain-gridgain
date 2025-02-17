/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.examples.ml.selection.scoring;

import java.io.FileNotFoundException;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ml.dataset.feature.extractor.Vectorizer;
import org.apache.ignite.ml.dataset.feature.extractor.impl.DummyVectorizer;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.selection.scoring.evaluator.Evaluator;
import org.apache.ignite.ml.selection.scoring.metric.classification.Accuracy;
import org.apache.ignite.ml.svm.SVMLinearClassificationModel;
import org.apache.ignite.ml.svm.SVMLinearClassificationTrainer;
import org.apache.ignite.ml.util.MLSandboxDatasets;
import org.apache.ignite.ml.util.SandboxMLCache;

/**
 * Run SVM classification trainer ({@link SVMLinearClassificationTrainer}) over distributed dataset.
 * <p>
 * Code in this example launches Ignite grid and fills the cache with test data points (based on the
 * <a href="https://en.wikipedia.org/wiki/Iris_flower_data_set"></a>Iris dataset</a>).</p>
 * <p>
 * After that it trains the model based on the specified data using
 * <a href="https://en.wikipedia.org/wiki/K-nearest_neighbors_algorithm">kNN</a> algorithm.</p>
 * <p>
 * Finally, this example loops over the test set of data points, applies the trained model to predict what cluster
 * does this point belong to, and compares prediction to expected outcome (ground truth).</p>
 * <p>
 * You can change the test data used in this example and re-run it to explore this algorithm further.</p>
 */
public class EvaluatorExample {
    /** Run example. */
    public static void main(String[] args) throws FileNotFoundException {
        System.out.println();
        System.out.println(">>> kNN multi-class classification algorithm over cached dataset usage example started.");
        // Start ignite grid.
        try (Ignite ignite = Ignition.start("examples/config/example-ignite.xml")) {
            System.out.println(">>> Ignite grid started.");

            IgniteCache<Integer, Vector> dataCache = null;
            try {
                dataCache = new SandboxMLCache(ignite).fillCacheWith(MLSandboxDatasets.TWO_CLASSED_IRIS);

                SVMLinearClassificationTrainer trainer = new SVMLinearClassificationTrainer();

                Vectorizer<Integer, Vector, Integer, Double> vectorizer = new DummyVectorizer<Integer>()
                    .labeled(Vectorizer.LabelCoordinate.FIRST);

                SVMLinearClassificationModel mdl = trainer.fit(ignite, dataCache, vectorizer);

                double accuracy = Evaluator.evaluate(
                    dataCache,
                    mdl,
                    vectorizer,
                    new Accuracy<>()
                );

                System.out.println("\n>>> Accuracy " + accuracy);

                double f1Score = Evaluator.evaluate(
                    dataCache,
                    mdl,
                    vectorizer
                ).f1Score();

                System.out.println("\n>>> F1-Score " + f1Score);
            } finally {
                dataCache.destroy();
            }
        }
    }
}
