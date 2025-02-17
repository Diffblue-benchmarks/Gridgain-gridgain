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

package org.apache.ignite.ml.math;

import java.awt.Color;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.ml.math.primitives.matrix.Matrix;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.structures.LabeledVector;
import org.apache.ignite.ml.util.generators.DataStreamGenerator;

/**
 * Utility methods to support output of {@link Vector} and {@link Matrix} instances to plain text or HTML.
 */
public class Tracer {
    /** Locale to format strings. */
    private static final Locale LOCALE = Locale.US;

    /**
     * Double to color mapper.
     */
    public interface ColorMapper extends Function<Double, Color> {
    }

    /**
     * Continuous red-to-blue color mapping.
     */
    private static ColorMapper defaultColorMapper(double min, double max) {
        double range = max - min;

        return new ColorMapper() {
            /** {@inheritDoc} */
            @Override public Color apply(Double d) {
                d = (d - min) / range;
                int r = (int)Math.round(255 * d);
                int g = 0;
                int b = (int)Math.round(255 * (1 - d));

                return new Color(r, g, b);
            }
        };
    }

    /**
     * Default vector color mapper implementation that map given double value to continuous red-blue (R_B) specter.
     *
     * @param vec Vector to map.
     * @return {@link ColorMapper} for the given vector.
     */
    private static ColorMapper mkVectorColorMapper(Vector vec) {
        return defaultColorMapper(vec.minValue(), vec.maxValue());
    }

    /**
     * Default matrix color mapper implementation that map given double value to continuous red-blue (R_B) specter.
     *
     * @param mtx Matrix to be mapped.
     * @return Color mapper for given matrix.
     */
    private static ColorMapper mkMatrixColorMapper(Matrix mtx) {
        return defaultColorMapper(mtx.minValue(), mtx.maxValue());
    }

    /**
     * @param vec Vector to show.
     * @param log {@link IgniteLogger} instance for output.
     * @param fmt Format string for vector elements.
     */
    public static void showAscii(Vector vec, IgniteLogger log, String fmt) {
        String cls = vec.getClass().getSimpleName();

        log.info(String.format(LOCALE, "%s(%d) [%s]", cls, vec.size(), mkString(vec, fmt)));
    }

    /**
     * @param vec Vector to show as plain text.
     * @param log {@link IgniteLogger} instance for output.
     */
    public static void showAscii(Vector vec, IgniteLogger log) {
        showAscii(vec, log, "%4f");
    }

    /**
     * @param vec Vector to show as plain text.
     * @param fmt Format string for vector elements.
     */
    public static void showAscii(Vector vec, String fmt) {
        String cls = vec.getClass().getSimpleName();

        System.out.println(asAscii(vec, fmt, true));
    }

    /**
     * @param vec Vector to show as plain text.
     * @param fmt Format string for vector elements.
     * @param showMeta Show vector type and size.
     */
    public static String asAscii(Vector vec, String fmt, boolean showMeta) {
        String cls = vec.getClass().getSimpleName();
        String vectorStr = mkString(vec, fmt);

        if (showMeta)
            return String.format(LOCALE, "%s(%d) [%s]", cls, vec.size(), vectorStr);
        else
            return String.format(LOCALE, "[%s]", vectorStr);
    }

    /**
     * @param mtx Matrix to show as plain text.
     */
    public static void showAscii(Matrix mtx) {
        showAscii(mtx, "%4f");
    }

    /**
     * @param mtx Matrix to show.
     * @param row Matrix row to output.
     * @param fmt Format string for matrix elements in the row.
     * @return String representation of given matrix row according to given format.
     */
    private static String rowStr(Matrix mtx, int row, String fmt) {
        StringBuilder buf = new StringBuilder();

        boolean first = true;

        int cols = mtx.columnSize();

        for (int col = 0; col < cols; col++) {
            String s = String.format(LOCALE, fmt, mtx.get(row, col));

            if (!first)
                buf.append(", ");

            buf.append(s);

            first = false;
        }

        return buf.toString();
    }

    /**
     * @param mtx {@link Matrix} object to show as a plain text.
     * @param fmt Format string for matrix rows.
     */
    public static void showAscii(Matrix mtx, String fmt) {
        System.out.println(asAscii(mtx, fmt));
    }

    /**
     * @param mtx {@link Matrix} object to show as a plain text.
     * @param fmt Format string for matrix rows.
     */
    public static String asAscii(Matrix mtx, String fmt) {
        StringBuilder builder = new StringBuilder();
        String cls = mtx.getClass().getSimpleName();

        int rows = mtx.rowSize();
        int cols = mtx.columnSize();

        builder.append(String.format(LOCALE, "%s(%dx%d)\n", cls, rows, cols));

        for (int row = 0; row < rows; row++)
            builder.append(rowStr(mtx, row, fmt)).append(row != rows - 1 ? "\n" : "");
        return builder.toString();
    }

    /**
     * @param mtx {@link Matrix} object to show as a plain text.
     * @param log {@link IgniteLogger} instance to output the logged matrix.
     * @param fmt Format string for matrix rows.
     */
    public static void showAscii(Matrix mtx, IgniteLogger log, String fmt) {
        String cls = mtx.getClass().getSimpleName();

        int rows = mtx.rowSize();
        int cols = mtx.columnSize();

        log.info(String.format(LOCALE, "%s(%dx%d)", cls, rows, cols));

        for (int row = 0; row < rows; row++)
            log.info(rowStr(mtx, row, fmt));
    }

    /**
     * @param vec {@link Vector} object to show as a plain text.
     */
    public static void showAscii(Vector vec) {
        showAscii(vec, "%4f");
    }

    /**
     * Saves given vector as CSV file.
     *
     * @param vec Vector to save.
     * @param fmt Format to use.
     * @param filePath Path of the file to save to.
     */
    public static void saveAsCsv(Vector vec, String fmt, String filePath) throws IOException {
        String s = mkString(vec, fmt);

        Files.write(Paths.get(filePath), s.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /**
     * Saves given matrix as CSV file.
     *
     * @param mtx Matrix to save.
     * @param fmt Format to use.
     * @param filePath Path of the file to save to.
     */
    public static void saveAsCsv(Matrix mtx, String fmt, String filePath) throws IOException {
        String s = mkString(mtx, fmt);

        Files.write(Paths.get(filePath), s.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /**
     * Shows given matrix in the browser with D3-based visualization.
     *
     * @param mtx Matrix to show.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Matrix mtx) throws IOException {
        showHtml(mtx, false);
    }

    /**
     * Shows given matrix in the browser with D3-based visualization.
     *
     * @param mtx Matrix to show.
     * @param useAsciiFallback Use ascii fallback is desktop or browser is unavailable.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Matrix mtx, boolean useAsciiFallback) throws IOException {
        showHtml(mtx, mkMatrixColorMapper(mtx), useAsciiFallback);
    }

    /**
     * Shows given matrix in the browser with D3-based visualization.
     *
     * @param mtx Matrix to show.
     * @param cm Optional color mapper. If not provided - red-to-blue (R_B) mapper will be used.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Matrix mtx, ColorMapper cm) throws IOException {
        showHtml(mtx, cm, false);
    }

    /**
     * Shows given matrix in the browser with D3-based visualization.
     *
     * @param mtx Matrix to show.
     * @param cm Optional color mapper. If not provided - red-to-blue (R_B) mapper will be used.
     * @param useAsciiFallback Use ascii fallback is desktop or browser is unavailable.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Matrix mtx, ColorMapper cm, boolean useAsciiFallback) throws IOException {
        if (!isBrowseSupported() && useAsciiFallback)
            showAscii(mtx);
        else {
            // Read it every time so that we can change it at runtime.
            String tmpl = fileToString("d3-matrix-template.html");

            String cls = mtx.getClass().getSimpleName();

            double min = mtx.minValue();
            double max = mtx.maxValue();

            openHtmlFile(tmpl.
                replaceAll("/\\*@NAME@\\*/.*\n", "var name = \"" + cls + "\";\n").
                replaceAll("/\\*@MIN@\\*/.*\n", "var min = " + dataColorJson(min, cm.apply(min)) + ";\n").
                replaceAll("/\\*@MAX@\\*/.*\n", "var max = " + dataColorJson(max, cm.apply(max)) + ";\n").
                replaceAll("/\\*@DATA@\\*/.*\n", "var data = " + mkJsArrayString(mtx, cm) + ";\n")
            );
        }
    }

    /**
     * Shows given vector in the browser with D3-based visualization.
     *
     * @param vec Vector to show.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Vector vec) throws IOException {
        showHtml(vec, false);
    }

    /**
     * Shows given vector in the browser with D3-based visualization.
     *
     * @param vec Vector to show.
     * @param useAsciiFallback Use ascii fallback is desktop or browser is unavailable.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Vector vec, boolean useAsciiFallback) throws IOException {
        showHtml(vec, mkVectorColorMapper(vec), useAsciiFallback);
    }

    /**
     * Open browser and shows given dataset generator's data on two dimensional plane. Label of vectors
     * is used for ordinate representation.
     *
     * @param name Name of dataset for showing.
     * @param generator Datastream generator.
     * @param limit Count of points that should be taken from dataset.
     * @param xIdx Index of dimension for abscissa representation.
     * @throws IOException IOException.
     */
    public static void showRegressionDatasetInHtml(String name, DataStreamGenerator generator, int limit,
        int xIdx) throws IOException {

        List<LabeledVector<Double>> values = generator.labeled().limit(limit).collect(Collectors.toList());
        showVectorsHtml(name, values, xIdx, LabeledVector::label, v -> Color.BLUE);
    }

    /**
     * Open browser and shows given dataset generator's data on two dimensional plane. Label of vectors
     * is used for ordinate representation.
     *
     * @param generator Datastream generator.
     * @param limit Count of points that should be taken from dataset.
     * @param xIdx Index of dimension for abscissa representation.
     * @throws IOException IOException.
     */
    public static void showRegressionDatasetInHtml(DataStreamGenerator generator, int limit,
        int xIdx) throws IOException {

        showRegressionDatasetInHtml("Regression dataset", generator, limit, xIdx);
    }

    /**
     * Open browser and shows given dataset generator's data on two dimensional plane.
     *
     * @param generator Datastream generator.
     * @param limit Count of points that should be taken from dataset.
     * @param xIdx Index of dimension for abscissa representation.
     * @param yIdx Index of dimension for ordinate representation.
     * @param isLabeled if isLabeled == true then colors will be used for separate different classes on plane.
     * @throws IOException IOException.
     */
    public static void showClassificationDatasetHtml(DataStreamGenerator generator, int limit,
        int xIdx, int yIdx, boolean isLabeled) throws IOException {

        showClassificationDatasetHtml("Classification dataset", generator, limit, xIdx, yIdx, isLabeled);
    }

    /**
     * Open browser and shows given dataset generator's data on two dimensional plane.
     *
     * @param name Name of dataset for showing.
     * @param generator Datastream generator.
     * @param limit Count of points that should be taken from dataset.
     * @param xIdx Index of dimension for abscissa representation.
     * @param yIdx Index of dimension for ordinate representation.
     * @param isLabeled if isLabeled == true then colors will be used for separate different classes on plane.
     * @throws IOException IOException.
     */
    public static void showClassificationDatasetHtml(String name, DataStreamGenerator generator, int limit,
        int xIdx, int yIdx, boolean isLabeled) throws IOException {

        List<LabeledVector<Double>> values = generator.labeled().limit(limit).collect(Collectors.toList());
        Map<Integer, Color> labelsMapping = new HashMap<>();
        if (isLabeled) {
            Set<Double> lbls = values.stream().map(LabeledVector::label).collect(Collectors.toSet());
            Random rnd = new Random(0);
            for (Double label : lbls)
                labelsMapping.put(label.intValue(), new Color(rnd.nextInt()));
        }

        showVectorsHtml(name, values, xIdx,
            v -> v.features().get(yIdx),
            v -> isLabeled ? labelsMapping.get(v.label().intValue()) : Color.BLUE
        );
    }

    /**
     * @param name Dataset name for showing.
     * @param values List of vectors are taken from dataset generator.
     * @param xIndex Index of abscissa in vector.
     * @param yGetter Getter of ordinate value from vector.
     * @param colorGetter Getter of collor for showing.
     * @throws IOException IOException.
     */
    private static void showVectorsHtml(String name, List<LabeledVector<Double>> values,
        int xIndex, Function<LabeledVector<Double>, Double> yGetter,
        Function<LabeledVector<Double>, Color> colorGetter) throws IOException {

        if(!isBrowseSupported())
            return;

        String tmpl = fileToString("d3-dataset-template.html");

        String data = values.stream().map(v -> {
            double x = v.features().get(xIndex);
            double y = yGetter.apply(v);
            Color c = colorGetter.apply(v);

            return dataColorJson(x, y, c);
        }).collect(Collectors.joining(",", "[", "]"));

        openHtmlFile(tmpl.
            replaceAll("/\\*@NAME@\\*/.*\n", "var name = \"" + name + "\";\n").
            replaceAll("/\\*@DATA@\\*/.*\n", "var data = " + data + ";\n")
        );
    }

    /**
     * Serialize xy-pair with vector to JSON representation.
     *
     * @param x X-value.
     * @param y Y-value.
     * @param clr Color.
     */
    private static String dataColorJson(double x, double y, Color clr) {
        return "{" +
            "x: " + String.format(LOCALE, "%4f", x) +
            ", y: " + String.format(LOCALE, "%4f", y) +
            ", r: " + clr.getRed() +
            ", g: " + clr.getGreen() +
            ", b: " + clr.getBlue() +
            "}";
    }

    /**
     * @param d Value of {@link Matrix} or {@link Vector} element.
     * @param clr {@link Color} to paint.
     * @return JSON representation for given value and color.
     */
    private static String dataColorJson(double d, Color clr) {
        return "{" +
            "d: " + String.format(LOCALE, "%4f", d) +
            ", r: " + clr.getRed() +
            ", g: " + clr.getGreen() +
            ", b: " + clr.getBlue() +
            "}";
    }

    /**
     * Shows given vector in the browser with D3-based visualization.
     *
     * @param vec Vector to show.
     * @param cm Optional color mapper. If not provided - red-to-blue (R_B) mapper will be used.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Vector vec, ColorMapper cm) throws IOException {
        showHtml(vec, cm, false);
    }

    /**
     * Shows given vector in the browser with D3-based visualization.
     *
     * @param vec Vector to show.
     * @param cm Optional color mapper. If not provided - red-to-blue (R_B) mapper will be used.
     * @param useAsciiFallback Use ascii fallback is desktop or browser is unavailable.
     * @throws IOException Thrown in case of any errors.
     */
    public static void showHtml(Vector vec, ColorMapper cm, boolean useAsciiFallback) throws IOException {
        if (!isBrowseSupported() && useAsciiFallback)
            showAscii(vec);
        else {
            // Read it every time so that we can change it at runtime.
            String tmpl = fileToString("d3-vector-template.html");

            String cls = vec.getClass().getSimpleName();

            double min = vec.minValue();
            double max = vec.maxValue();

            openHtmlFile(tmpl.
                replaceAll("/\\*@NAME@\\*/.*\n", "var name = \"" + cls + "\";\n").
                replaceAll("/\\*@MIN@\\*/.*\n", "var min = " + dataColorJson(min, cm.apply(min)) + ";\n").
                replaceAll("/\\*@MAX@\\*/.*\n", "var max = " + dataColorJson(max, cm.apply(max)) + ";\n").
                replaceAll("/\\*@DATA@\\*/.*\n", "var data = " + mkJsArrayString(vec, cm) + ";\n")
            );
        }
    }

    /**
     * Returns {@code true} if browse can be used (to show HTML for example), otherwise returns {@code false}.
     *
     * @return {@code true} if browse can be used (to show HTML for example), otherwise returns {@code false}
     */
    private static boolean isBrowseSupported() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
    }

    /**
     * Reads file content into the string.
     *
     * @param fileName Name of the file (on classpath) to read.
     * @return Content of the file.
     * @throws IOException If an I/O error of some sort has occurred.
     */
    private static String fileToString(String fileName) throws IOException {
        assert Tracer.class.getResourceAsStream(fileName) != null : "Can't get resource: " + fileName;

        try (InputStreamReader is
                 = new InputStreamReader(Tracer.class.getResourceAsStream(fileName), StandardCharsets.US_ASCII)) {

            return new BufferedReader(is).lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Opens file in the browser with given HTML content.
     *
     * @param html HTML content.
     * @throws IOException Thrown in case of any errors.
     */
    private static void openHtmlFile(String html) throws IOException {
        File temp = File.createTempFile(IgniteUuid.randomUuid().toString(), ".html");

        BufferedWriter bw = new BufferedWriter(new FileWriter(temp));

        bw.write(html);

        bw.close();

        Desktop.getDesktop().browse(temp.toURI());
    }

    /**
     * Gets string presentation of this vector.
     *
     * @param vec Vector to string-ify.
     * @param fmt {@link String#format(Locale, String, Object...)} format.
     */
    private static String mkString(Vector vec, String fmt) {
        boolean first = true;

        StringBuilder buf = new StringBuilder();

        for (Vector.Element x : vec.all()) {
            String s = String.format(LOCALE, fmt, x.get());

            if (!first) {
                buf.append(", ");
                buf.append(s);
            }
            else {
                buf.append(s);
                first = false;
            }
        }

        return buf.toString();
    }

    /**
     * Gets JavaScript array presentation of this vector.
     *
     * @param vec Vector to JavaScript-ify.
     * @param cm Color mapper to user.
     */
    private static String mkJsArrayString(Vector vec, ColorMapper cm) {
        boolean first = true;

        StringBuilder buf = new StringBuilder();

        for (Vector.Element x : vec.all()) {
            double d = x.get();

            String s = dataColorJson(d, cm.apply(d));

            if (!first)
                buf.append(", ");

            buf.append(s);

            first = false;
        }

        return '[' + buf.toString() + ']';
    }

    /**
     * Gets JavaScript array presentation of this vector.
     *
     * @param mtx Matrix to JavaScript-ify.
     * @param cm Color mapper to user.
     */
    private static String mkJsArrayString(Matrix mtx, ColorMapper cm) {
        boolean first = true;

        StringBuilder buf = new StringBuilder();

        int rows = mtx.rowSize();
        int cols = mtx.columnSize();

        for (int row = 0; row < rows; row++) {
            StringBuilder rowBuf = new StringBuilder();

            boolean rowFirst = true;

            for (int col = 0; col < cols; col++) {
                double d = mtx.get(row, col);

                String s = dataColorJson(d, cm.apply(d));

                if (!rowFirst)
                    rowBuf.append(", ");

                rowBuf.append(s);

                rowFirst = false;
            }

            if (!first)
                buf.append(", ");

            buf.append('[').append(rowBuf.toString()).append(']');

            first = false;
        }

        return '[' + buf.toString() + ']';
    }

    /**
     * @param mtx Matrix to log.
     * @param fmt Output format.
     * @return Formatted representation of a matrix.
     */
    private static String mkString(Matrix mtx, String fmt) {
        StringBuilder buf = new StringBuilder();

        int rows = mtx.rowSize();
        int cols = mtx.columnSize();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String s = String.format(LOCALE, fmt, mtx.get(row, col));

                if (col != 0)
                    buf.append(", ");

                buf.append(s);

                if (col == cols - 1 && row != rows - 1)
                    buf.append(",\n");

            }
        }

        return buf.toString();
    }
}
