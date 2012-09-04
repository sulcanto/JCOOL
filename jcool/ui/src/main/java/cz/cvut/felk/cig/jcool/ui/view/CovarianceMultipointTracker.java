/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.cvut.felk.cig.jcool.ui.view;

import cz.cvut.felk.cig.jcool.core.Function;
import cz.cvut.felk.cig.jcool.core.Point;
import cz.cvut.felk.cig.jcool.core.Producer;
import cz.cvut.felk.cig.jcool.core.ValuePoint;
import cz.cvut.felk.cig.jcool.core.ValuePointListTelemetry;
import cz.cvut.felk.cig.jcool.experiment.Iteration;
import cz.cvut.felk.cig.jcool.experiment.TelemetryVisualization;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JPanel;
import org.apache.commons.collections.Closure;
import org.apache.commons.math.linear.*;
import org.apache.commons.math.stat.correlation.Covariance;
import org.apache.log4j.Logger;
import org.opensourcephysics.display.DrawingPanel;
import org.opensourcephysics.display2d.GridPointData;
import org.opensourcephysics.display2d.InterpolatedPlot;
import org.ytoh.configurations.PropertyState;
import org.ytoh.configurations.annotations.Component;
import org.ytoh.configurations.annotations.Property;
import org.ytoh.configurations.annotations.Range;

/**
 *
 * @author ytoh
 */
@Component(name = "Show covariance of all points")
public class CovarianceMultipointTracker implements TelemetryVisualization<ValuePointListTelemetry> {

    static Logger logger = Logger.getLogger(MultiPointTracker.class);

    /**
     * Marked container for optimization runs.
     *
     * <p>Every run has its own container of points and its own vertex marker.
     * It also can be switched on and off.</p>
     */
    public enum Slot {

        CIRCLE, SQUARE, CROSS, TRIANGLE;
        private final List<List<ValuePoint>> points = new CopyOnWriteArrayList<List<ValuePoint>>();
        private boolean show;

        public boolean isShow() {
            return show;
        }

        public void setShow(boolean show) {
            this.show = show;
        }

        public List<List<ValuePoint>> getPoints() {
            return points;
        }

        public void clearList() {
            points.clear();
        }
    }
    @Property(name = "Resolution", description = "Resolution defines how fine grained function sampling will be (the higher the better)")
    @Range(from = 1, to = 1000)
    private int resolution = 200;

    public int getResolution() {
        return resolution;
    }

    public void setResolution(int resolution) {
        this.resolution = resolution;
    }
    @Property(name = "Number of points", description = "The number of points in the trail to render")
    @Range(from = 1, to = Integer.MAX_VALUE)
    private int maxPoints = 2;

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }
    @Property(name = "Buffer", description = "How much space should there be between a point and the edge")
    @Range(from = 0, to = Integer.MAX_VALUE)
    private double buffer = 1;

    public double getBuffer() {
        return buffer;
    }

    public void setBuffer(double buffer) {
        this.buffer = buffer;
    }
    @Property(name = "Center X", description = "X coordinate of center point at visualization start")
    private double centerX = 0;

    public double getCenterX() {
        return centerX;
    }

    public void setCenterX(double centerX) {
        this.centerX = centerX;
    }
    @Property(name = "Center Y", description = "Y coordinate of center point at visualization start")
    private double centerY = 0;

    public double getCenterY() {
        return centerY;
    }

    public void setCenterY(double centerY) {
        this.centerY = centerY;
    }
    @Property(name = "Show circle slot")
    private boolean showCircleSlot;

    public boolean isShowCircleSlot() {
        return Slot.CIRCLE.isShow();
    }

    public void setShowCircleSlot(boolean showCircleSlot) {
        Slot.CIRCLE.setShow(showCircleSlot);
    }

    public PropertyState getShowCircleSlotState() {
        return Slot.CIRCLE.getPoints().isEmpty() ? PropertyState.DISABLED : PropertyState.ENABLED;
    }
    @Property(name = "Show square slot")
    private boolean showSquareSlot;

    public boolean isShowSquareSlot() {
        return Slot.SQUARE.isShow();
    }

    public void setShowSquareSlot(boolean showSquareSlot) {
        Slot.SQUARE.setShow(showSquareSlot);
    }

    public PropertyState getShowSquareSlotState() {
        return Slot.SQUARE.getPoints().isEmpty() ? PropertyState.DISABLED : PropertyState.ENABLED;
    }
    @Property(name = "Show triangle slot")
    private boolean showTriangleSlot;

    public boolean isShowTriangleSlot() {
        return Slot.TRIANGLE.isShow();
    }

    public void setShowTriangleSlot(boolean showTriangleSlot) {
        Slot.TRIANGLE.setShow(showTriangleSlot);
    }

    public PropertyState getShowTriangleSlotState() {
        return Slot.TRIANGLE.getPoints().isEmpty() ? PropertyState.DISABLED : PropertyState.ENABLED;
    }
    @Property(name = "Show x slot")
    private boolean showCrossSlot;

    public boolean isShowCrossSlot() {
        return Slot.CROSS.isShow();
    }

    public void setShowCrossSlot(boolean showCrossSlot) {
        Slot.CROSS.setShow(showCrossSlot);
    }

    public PropertyState getShowCrossSlotState() {
        return Slot.CROSS.getPoints().isEmpty() ? PropertyState.DISABLED : PropertyState.ENABLED;
    }
    @Property(name = "Use slot")
    private Slot useSlot = Slot.CIRCLE;

    public Slot getUseSlot() {
        return useSlot;
    }

    public void setUseSlot(Slot useSlot) {
        this.useSlot = useSlot;
    }

    // Private data
    private GridPointData data;
    private InterpolatedPlot plot;
    private JPanel panel;
    private DrawingPanel drawablePanel;
    private TreeSet<ValuePoint> xAxis = new TreeSet<ValuePoint>(new Comparator<ValuePoint>() {

        public int compare(ValuePoint o1, ValuePoint o2) {
            double xCoordinate1 = o1.getPoint().toArray()[0];
            double xCoordinate2 = o2.getPoint().toArray()[0];

            if (xCoordinate1 == xCoordinate2) {
                return 0;
            } else {
                if (xCoordinate1 < xCoordinate2) {
                    return -1;
                } else {
                    return 1;
                }
            }
        }
    });
    private TreeSet<ValuePoint> yAxis = new TreeSet<ValuePoint>(new Comparator<ValuePoint>() {

        public int compare(ValuePoint o1, ValuePoint o2) {
            double yCoordinate1 = o1.getPoint().toArray()[1];
            double yCoordinate2 = o2.getPoint().toArray()[1];

            if (yCoordinate1 == yCoordinate2) {
                return 0;
            } else {
                if (yCoordinate1 < yCoordinate2) {
                    return -1;
                } else {
                    return 1;
                }
            }
        }
    });
    private Function lastFunction = null;

    //private Map<Point,Double> cache = new HashMap<Point, Double>(10000);
    private Closure datasetRecreation = new Closure() { // NullObject

        public void execute(Object input) {
        }
    };

    public void init(final Function function) {
        xAxis.clear();
        yAxis.clear();

        if (function != lastFunction) { // need to reset
            Slot.CIRCLE.clearList();
            Slot.CROSS.clearList();
            Slot.SQUARE.clearList();
            Slot.TRIANGLE.clearList();
        }

        data = new GridPointData(resolution, resolution, 1);
        plot = new InterpolatedPlot(data);

        useSlot.clearList();
        final List<List<ValuePoint>> points = useSlot.getPoints();
        for (Slot slot : Slot.values()) {
            if (slot.isShow()) {
                for (List<ValuePoint> valuePoints : slot.getPoints()) {
                    for (ValuePoint valuePoint : valuePoints) {
                        xAxis.add(valuePoint);
                        yAxis.add(valuePoint);
                    }
                }
            }
        }

        // clean drawing panel
        drawablePanel.removeDrawables(InterpolatedPlot.class);
        drawablePanel.addDrawable(plot);

        datasetRecreation = new Closure() {

            public void execute(Object input) {
                List<ValuePoint> vps = (List<ValuePoint>) input;
                points.add(vps);
                for (ValuePoint vp : vps) {
                    xAxis.add(vp);
                    yAxis.add(vp);
                }

                if (points.size() > maxPoints) {
                    List<ValuePoint> removed = points.remove(0);
                    for (ValuePoint valuePoint : removed) {
                        xAxis.remove(valuePoint);
                        yAxis.remove(valuePoint);
                    }
                }

                // for ilustration
                if (xAxis.size() > 0 && yAxis.size() > 0) {
                    double lowerXBound = Math.min(data.getLeft(), Math.floor(xAxis.first().getPoint().toArray()[0] - buffer));
                    double upperXBound = Math.max(data.getRight(), Math.ceil(xAxis.last().getPoint().toArray()[0] + buffer));
                    double lowerYBound = Math.min(data.getBottom(), Math.floor(yAxis.first().getPoint().toArray()[1] - buffer));
                    double upperYBound = Math.max(data.getTop(), Math.ceil(yAxis.last().getPoint().toArray()[1] + buffer));

                    data.setScale(lowerXBound, upperXBound, lowerYBound, upperYBound);
                    double[][][] xyz = data.getData();

                    for (int i = 0; i < resolution; i++) {
                        for (int j = 0; j < resolution; j++) {
                            Point p = Point.at(xyz[i][j]);
                            // check the cached value of the point
                            xyz[i][j][2] = /*cache.containsKey(p) ? cache.get(p) :*/ function.valueAt(p);
                        }
                    }
                }

                plot.setGridData(data);

                drawablePanel.repaint();
            }
        };

        lastFunction = function;

        // initialize to center point
        double lowerXBound = Math.floor(centerX - buffer);
        double upperXBound = Math.ceil(centerX + buffer);
        double lowerYBound = Math.floor(centerY - buffer);
        double upperYBound = Math.ceil(centerY + buffer);

        data.setScale(lowerXBound, upperXBound, lowerYBound, upperYBound);
        double[][][] xyz = data.getData();

        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                Point p = Point.at(xyz[i][j]);
                // check the cached value of the point
                xyz[i][j][2] = /*cache.containsKey(p) ? cache.get(p) :*/ function.valueAt(p);
            }
        }

        plot.setGridData(data);
        drawablePanel.repaint();
    }

    public void attachTo(JPanel panel) {
        this.panel = panel;
        panel.removeAll();

        panel.setLayout(new BorderLayout());

        drawablePanel = new PointDrawingPanel();

        panel.add(drawablePanel, BorderLayout.CENTER);
    }

    public Class<ValuePointListTelemetry> getAcceptableType() {
        return ValuePointListTelemetry.class;
    }

    public void notifyOf(Producer<? extends Iteration<ValuePointListTelemetry>> producer) {
        Iteration<ValuePointListTelemetry> i = producer.getValue();

        if (i != null && i.getValue() != null) {
            datasetRecreation.execute(i.getValue().getValue());
        }
    }

    /**
     *
     */
    private class PointDrawingPanel extends DrawingPanel {

        private final int RADIUS = 6;
        private final Stroke LINE = new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        public PointDrawingPanel() {
            setSquareAspect(true);
            enableInspector(true);
            setShowCoordinates(true);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2d = (Graphics2D) g;

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            for (Slot slot : Slot.values()) {
                if (slot.isShow() || slot == useSlot) {
                    paintSequence(slot, slot.getPoints(), Color.BLACK, g2d);
                }
            }
        }

        /**
         * author sulcanto
         * @param points
         * @param color
         * @param g
         *
         */
        private void paintSequence(Slot type, List<List<ValuePoint>> points, Color color, Graphics2D g) {
            int i = 0;
            int size = points.size();

            RealMatrix samples = MatrixUtils.createRealMatrix(720, 2);
            for(int j=0; j<samples.getRowDimension(); j++){
                double deg = 360 * j/samples.getRowDimension();
                samples.setRow(j,new double[]{Math.cos(j),Math.sin(j)});
            }

            if (size > 0) {

                //
                // draws points
                //
                double increment = 200 / size;

                for (List<ValuePoint> valuePoints : points) {
                    int alpha = (int) (i * increment) + 55;
                    Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);

                    // generate matrix from current population popsize x 2
                    RealMatrix currentGenerationMatrix = MatrixUtils.createRealMatrix(valuePoints.size(), 2);
                    for(int j = 0; j < valuePoints.size(); j++){
                        currentGenerationMatrix.setRow(j, valuePoints.get(j).getPoint().toArray());
                    }

                    RealMatrix cov = new Covariance(currentGenerationMatrix).getCovarianceMatrix();

                    // estimate mean
                    double [] mean = new double[2];
                    for (int k = 0; k < currentGenerationMatrix.getRowDimension(); k++){
                        mean[0] += currentGenerationMatrix.getEntry(k,0);
                        mean[1] += currentGenerationMatrix.getEntry(k,1);
                    }
                    mean[0]/=currentGenerationMatrix.getRowDimension();
                    mean[1]/=currentGenerationMatrix.getRowDimension();

                    // calculate eigendecomposition
                    EigenDecomposition eig=new EigenDecompositionImpl(cov,0);
                    for(int k = 0; k<samples.getRowDimension(); k++){
                        RealVector pt = cov.operate(samples.getRowVector(k));

                        g.drawOval(xToPix(pt.getEntry(0) + mean[0]), yToPix(pt.getEntry(1) + mean[1]), 1, 1);
                    }
                    g.setColor(c);

                    i++;

                }
            }
        }
    }
}
