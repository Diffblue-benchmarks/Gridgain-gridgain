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

package org.apache.ignite.examples.ml.tree;

import org.apache.commons.math3.util.Precision;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.ml.dataset.feature.extractor.impl.LabeledDummyVectorizer;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;
import org.apache.ignite.ml.structures.LabeledVector;
import org.apache.ignite.ml.tree.DecisionTreeClassificationTrainer;
import org.apache.ignite.ml.tree.DecisionTreeNode;

import java.util.Random;

/**
 * Example of using distributed {@link DecisionTreeClassificationTrainer}.
 * <p>
 * Code in this example launches Ignite grid and fills the cache with pseudo random training data points.</p>
 * <p>
 * After that it creates classification trainer and uses it to train the model on the training set.</p>
 * <p>
 * Finally, this example loops over the pseudo randomly generated test set of data points, applies the trained model,
 * and compares prediction to expected outcome.</p>
 * <p>
 * You can change the test data used in this example and re-run it to explore this algorithm further.</p>
 */
public class DecisionTreeClassificationTrainerExample {
    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     */
    public static void main(String... args) {
        System.out.println(">>> Decision tree classification trainer example started.");

        // Start ignite grid.
        try (Ignite ignite = Ignition.start("examples/config/example-ignite.xml")) {
            System.out.println(">>> Ignite grid started.");

            // Create cache with training data.
            CacheConfiguration<Integer, LabeledVector<Double>> trainingSetCfg = new CacheConfiguration<>();
            trainingSetCfg.setName("TRAINING_SET");
            trainingSetCfg.setAffinity(new RendezvousAffinityFunction(false, 10));

            IgniteCache<Integer, LabeledVector<Double>> trainingSet = null;
            try {
                trainingSet = ignite.createCache(trainingSetCfg);

                Random rnd = new Random(0);

                // Fill training data.
                for (int i = 0; i < 1000; i++)
                    trainingSet.put(i, generatePoint(rnd));

                // Create classification trainer.
                DecisionTreeClassificationTrainer trainer = new DecisionTreeClassificationTrainer(4, 0);

                // Train decision tree model.
                LabeledDummyVectorizer<Integer, Double> vectorizer = new LabeledDummyVectorizer<>();
                DecisionTreeNode mdl = trainer.fit(
                    ignite,
                    trainingSet,
                    vectorizer
                );

                System.out.println(">>> Decision tree classification model: " + mdl);

                // Calculate score.
                int correctPredictions = 0;
                for (int i = 0; i < 1000; i++) {
                    LabeledVector<Double> pnt = generatePoint(rnd);

                    double prediction = mdl.predict(pnt.features());
                    double lbl = pnt.label();

                    if (i % 50 == 1)
                        System.out.printf(">>> test #: %d\t\t predicted: %.4f\t\tlabel: %.4f\n", i, prediction, lbl);

                    if (Precision.equals(prediction, lbl, Precision.EPSILON))
                        correctPredictions++;
                }

                System.out.println(">>> Accuracy: " + correctPredictions / 10.0 + "%");
                System.out.println(">>> Decision tree classification trainer example completed.");
            } finally {
                trainingSet.destroy();
            }
        }
    }

    /**
     * Generate point with {@code x} in (-0.5, 0.5) and {@code y} in the same interval. If {@code x * y > 0} then label
     * is 1, otherwise 0.
     *
     * @param rnd Random.
     * @return Point with label.
     */
    private static LabeledVector<Double> generatePoint(Random rnd) {

        double x = rnd.nextDouble() - 0.5;
        double y = rnd.nextDouble() - 0.5;

        return new LabeledVector<>(VectorUtils.of(x,y), x * y > 0 ? 1. : 0.);
    }
}
