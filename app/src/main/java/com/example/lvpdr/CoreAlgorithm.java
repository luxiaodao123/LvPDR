package com.example.lvpdr;


import android.util.Log;

import com.example.lvpdr.core.LocationTrack;
import com.example.lvpdr.core.model.LocationWithDistance;
import com.example.lvpdr.data.cache.RedisCache;
import com.mapbox.geojson.Point;
import com.mapbox.turf.TurfClassification;
import com.mapbox.turf.TurfMeasurement;
import com.mapbox.turf.TurfMisc;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import uk.me.berndporr.iirj.*;

import java.util.ArrayList;
import java.util.List;


public class CoreAlgorithm {
    private static final String TAG = "CoreAlgorithm";
    private  static  ArrayList<float[]> accelerationData = new ArrayList<float[]>();
    private CRSFactory crsFactory = new CRSFactory();
    private CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
    final  private  CoordinateReferenceSystem epsg4326 = crsFactory.createFromName("epsg:4326");
    final  private CoordinateReferenceSystem epsg3857 = crsFactory.createFromName("epsg:3857");
    private double sampleRate;
    private double height;
    private double K;  //Coefficient (may need to be adjusted for different individuals)
    private static CoreAlgorithm singleton = null;

    public CoreAlgorithm(double sampleRate, double height, double K) {
        this.sampleRate = sampleRate;
        this.height = height;
        this.K = K;
        singleton = this;
    }

    public static CoreAlgorithm getInstance() {return singleton;}

    final public class Matrix {
        private final int M;             // number of rows
        private final int N;             // number of columns
        private final double[][] data;   // M-by-N array

        // create M-by-N matrix of 0's
        public Matrix(int M, int N) {
            this.M = M;
            this.N = N;
            data = new double[M][N];
        }

        // create matrix based on 2d array
        public Matrix(double[][] data) {
            M = data.length;
            N = data[0].length;
            this.data = new double[M][N];
            for (int i = 0; i < M; i++)
                for (int j = 0; j < N; j++)
                    this.data[i][j] = data[i][j];
        }

        // copy constructor
        private Matrix(Matrix A) {
            this(A.data);
        }

        // create and return a random M-by-N matrix with values between 0 and 1
        public Matrix random(int M, int N) {
            Matrix A = new Matrix(M, N);
            for (int i = 0; i < M; i++)
                for (int j = 0; j < N; j++)
                    A.data[i][j] = Math.random();
            return A;
        }

        // create and return the N-by-N identity matrix
        public Matrix identity(int N) {
            Matrix I = new Matrix(N, N);
            for (int i = 0; i < N; i++)
                I.data[i][i] = 1;
            return I;
        }

        // swap rows i and j
        private void swap(int i, int j) {
            double[] temp = data[i];
            data[i] = data[j];
            data[j] = temp;
        }

        // create and return the transpose of the invoking matrix
        public Matrix transpose() {
            Matrix A = new Matrix(N, M);
            for (int i = 0; i < M; i++)
                for (int j = 0; j < N; j++)
                    A.data[j][i] = this.data[i][j];
            return A;
        }

        // return C = A + B
        public Matrix plus(Matrix B) {
            Matrix A = this;
            if (B.M != A.M || B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.");
            Matrix C = new Matrix(M, N);
            for (int i = 0; i < M; i++)
                for (int j = 0; j < N; j++)
                    C.data[i][j] = A.data[i][j] + B.data[i][j];
            return C;
        }


        // return C = A - B
        public Matrix minus(Matrix B) {
            Matrix A = this;
            if (B.M != A.M || B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.");
            Matrix C = new Matrix(M, N);
            for (int i = 0; i < M; i++)
                for (int j = 0; j < N; j++)
                    C.data[i][j] = A.data[i][j] - B.data[i][j];
            return C;
        }

        // does A = B exactly?
        public boolean eq(Matrix B) {
            Matrix A = this;
            if (B.M != A.M || B.N != A.N) throw new RuntimeException("Illegal matrix dimensions.");
            for (int i = 0; i < M; i++)
                for (int j = 0; j < N; j++)
                    if (A.data[i][j] != B.data[i][j]) return false;
            return true;
        }

        // return C = A * B
        public Matrix times(Matrix B) {
            Matrix A = this;
            if (A.N != B.M) throw new RuntimeException("Illegal matrix dimensions.");
            Matrix C = new Matrix(A.M, B.N);
            for (int i = 0; i < C.M; i++)
                for (int j = 0; j < C.N; j++)
                    for (int k = 0; k < A.N; k++)
                        C.data[i][j] += (A.data[i][k] * B.data[k][j]);
            return C;
        }


        // return x = A^-1 b, assuming A is square and has full rank
        public Matrix solve(Matrix rhs) {
            if (M != N || rhs.M != N || rhs.N != 1)
                throw new RuntimeException("Illegal matrix dimensions.");

            // create copies of the data
            Matrix A = new Matrix(this);
            Matrix b = new Matrix(rhs);

            // Gaussian elimination with partial pivoting
            for (int i = 0; i < N; i++) {

                // find pivot row and swap
                int max = i;
                for (int j = i + 1; j < N; j++)
                    if (Math.abs(A.data[j][i]) > Math.abs(A.data[max][i]))
                        max = j;
                A.swap(i, max);
                b.swap(i, max);

                // singular
                if (A.data[i][i] == 0.0) throw new RuntimeException("Matrix is singular.");

                // pivot within b
                for (int j = i + 1; j < N; j++)
                    b.data[j][0] -= b.data[i][0] * A.data[j][i] / A.data[i][i];

                // pivot within A
                for (int j = i + 1; j < N; j++) {
                    double m = A.data[j][i] / A.data[i][i];
                    for (int k = i + 1; k < N; k++) {
                        A.data[j][k] -= A.data[i][k] * m;
                    }
                    A.data[j][i] = 0.0;
                }
            }

            // back substitution
            Matrix x = new Matrix(N, 1);
            for (int j = N - 1; j >= 0; j--) {
                double t = 0.0;
                for (int k = j + 1; k < N; k++)
                    t += A.data[j][k] * x.data[k][0];
                x.data[j][0] = (b.data[j][0] - t) / A.data[j][j];
            }
            return x;

        }

        //return inverse matrix
        public Matrix inverse() {
            Matrix A = this;
            Matrix inverse = new Matrix(M, N);
            Matrix identity = new Matrix(M, N);
            for (int i = 0; i < N; ++i) {
                identity.data[i][i] = 1;
            }
            Matrix augmentedMatrix = new Matrix(M, 2 * N);

            for (int i = 0; i < N; ++i) {
                System.arraycopy(A.data[i], 0, augmentedMatrix.data[i], 0, N);
                System.arraycopy(identity.data[i], 0, augmentedMatrix.data[i], N, N);
            }
            if (!gaussian(augmentedMatrix)) {
                return null;
            }
            for (int i = 0; i < N; ++i) {
                System.arraycopy(augmentedMatrix.data[i], N, inverse.data[i], 0, N);
            }
            return inverse;
        }

        public boolean gaussian(Matrix matrix) {
            int n = matrix.N;
            for (int i = 0; i < n; ++i) {
                int maxRow = i;
                for (int j = i + 1; j < n; ++j) {
                    if (Math.abs(matrix.data[j][i]) > Math.abs(matrix.data[maxRow][i])) {
                        maxRow = j;
                    }
                }
                swap(matrix, i, maxRow);
                if (matrix.data[i][i] == 0) {
                    return false;
                }
                for (int j = i + 1; j < n; ++j) {
                    double coef = matrix.data[j][i] / matrix.data[i][i];
                    for (int k = i + 1; k < 2 * n; ++k) {
                        matrix.data[j][k] -= matrix.data[i][k] * coef;
                    }
                    matrix.data[j][i] = 0;
                }
            }
            return true;
        }

        public void swap(Matrix matrix, int i, int j) {
            double[] temp = matrix.data[i];
            matrix.data[i] = matrix.data[j];
            matrix.data[j] = temp;
        }

    }

    class Kalmanfilter {
        private final Matrix matrix_zero = new Matrix(new double[][]{
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0}
        });
        private final Matrix Matrix_B = new Matrix(new double[][]{
                {1, 0, 0, 0},
                {0, 1, 0, 0},
                {0, 0, 1, 0},
                {0, 0, 0, 1}
        });
        private final Matrix Matrix_H = new Matrix(new double[][]{
                {1, 0, 1, 0},
                {0, 1, 0, 1},
                {0, 0, 0, 0},
                {0, 0, 0, 0}
        });
        private final Matrix Matrix_Q = new Matrix(new double[][]{
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0.1, 0},
                {0, 0, 0, 0.1}
        });//Action Uncertainty
        private final Matrix Matrix_R = new Matrix(new double[][]{
                {10, 0, 0, 0},
                {0, 10, 0, 0},
                {0, 0, 1, 0},
                {0, 0, 0, 1}
        });//Sensor Noise

        long stamp = 0;
        private Matrix P = new Matrix(new double[][]{
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0}
        });


        public Point pred(long newStamp, Point coord) {
            // Prediction Step:
            /*
                x = (A * x)
                P = (A * P * AT) + Q       ← AT is the matrix transpose of A
            */
            Matrix x = new Matrix(new double[][]{{coord.longitude()}, {coord.latitude()}, {0}, {0}});
            long time = (stamp == 0 ? 1 : newStamp - stamp);
            final Matrix A = new Matrix(new double[][]{
                    {1, 0, time, 0},
                    {0, 1, 0, time},
                    {0, 0, 1, 0},
                    {0, 0, 0, 1}
            });

            x = A.times(x);
            P = A.times(P).times(A.transpose()).plus(Matrix_Q);
            return Point.fromLngLat(x.data[0][0], x.data[1][0]);

        }

        public Point corr(long newStamp, Point coord) {
            Matrix x = new Matrix(new double[][]{{coord.longitude(), coord.latitude(), 0, 0}});
            Matrix m = new Matrix(new double[][]{{coord.longitude(), coord.latitude(), 0, 0}});
            long time = (stamp == 0 ? 1 : newStamp - stamp);
            /*
                Correction Step:
                S = (H * P * HT) + R       ← HT is the matrix transpose of H
                K = P * HT * S-1           ← S-1 is the matrix inverse of S
                y = m - (H * x)
                x = x + (K * y)
                P = (I - (K * H)) * P      ← I is the Identity matrix
            */
            Matrix S = Matrix_H.times(P).times(Matrix_H.transpose()).plus(Matrix_R);
            Matrix K = P.times(Matrix_H.transpose()).times(S.inverse());
            Matrix y = m.minus(Matrix_H.times(x));
            x = x.plus(K.times(y));
            P = P.times(P.identity(4).minus(K.times(Matrix_H)));

            double speed = Math.sqrt(x.data[2][0] * x.data[2][0] + x.data[3][0] * x.data[3][0]);
            if (speed >= 3) {
                x.data[2][0] = 3 * x.data[2][0] / speed;
                x.data[3][0] = 3 * x.data[3][0] / speed;
            }
            return Point.fromLngLat(x.data[0][0], x.data[1][0]);
        }
    }

    public double calculateStepLength(float[] data){
        //
        float[] accData = new float[3];
        System.arraycopy(data, 0, accData, 0, data.length);
        accelerationData.add(accData);
        ArrayList<int[]> peakValley = findPeakValley(accelerationData);
        int[] peaks = peakValley.get(0);
        int[] valleys = peakValley.get(1);
        if(peaks.length == 0 || valleys.length == 0)
            return 0.0;
        if(peaks.length == 1 || valleys.length == 1)
            return 0.0;
        float[] accMax = accelerationData.get(peaks[0]);
        float[] accMin = accelerationData.get(valleys[0]);
        double diff = Math.sqrt(Math.pow(accMax[0], 2) + Math.pow(accMax[1], 2) + Math.pow(accMax[2], 2)) -
                Math.sqrt(Math.pow(accMin[0], 2) + Math.pow(accMin[1], 2) + Math.pow(accMin[2], 2));
        if(peaks[0] < valleys[0]){
            //先波峰再波谷
            accelerationData = new ArrayList(accelerationData.subList(peaks[1], accelerationData.size()));
        }else {
            //先波谷再波峰
            accelerationData = new ArrayList(accelerationData.subList(valleys[1], accelerationData.size()));
        }
        //-0.04, 0.565
        //-0.19, 0.94
        return -0.04 * diff + 0.565 * Math.sqrt(Math.sqrt(diff));
    }

    private ArrayList findPeakValley(ArrayList<float[]> data){
        double max = 10.5f;
        int peakIndex = -1;
        int valleyIndex = -1;
        double min = 9.1f;
        int flag = 0;
        ArrayList<Integer> peaks = new ArrayList<>();
        ArrayList<Integer> valleys = new ArrayList<>();
        ArrayList<int[]> result = new ArrayList<>();
        for(int i = 0; i < data.size(); i++){
            double accSum = Math.sqrt(Math.pow(data.get(i)[0], 2) + Math.pow(data.get(i)[1], 2) + Math.pow(data.get(i)[2], 2));
            if(accSum < 9.8){
                if(flag != -1){
                    min = accSum;
                    flag = -1;
                    valleyIndex = i;
                    if(peakIndex != -1)
                        peaks.add(Integer.valueOf(peakIndex));
                    continue;
                }
                if(min > accSum){
                    min = accSum;
                    valleyIndex = i;
                }

            }else if(accSum > 9.8){
                if(flag != 1){
                    max = accSum;
                    flag = 1;
                    peakIndex = i;
                    if(valleyIndex != -1)
                        valleys.add(Integer.valueOf(valleyIndex));
                    continue;
                }
                if(max < accSum){
                    max = accSum;
                    peakIndex = i;
                }
            }else{
                //do nothing
            }
        }
        if(flag == -1)
            valleys.add(Integer.valueOf(valleyIndex));
        if(flag == 1)
            peaks.add(Integer.valueOf(peakIndex));
        result.add(peaks.stream().mapToInt(j -> j).toArray());
        result.add(valleys.stream().mapToInt(j -> j).toArray());
        return result;
    }

    public Point fusionLocation(Point pdr, Point gnss, double K) {
        //get weight by K
        Point fusionPoint = Point.fromLngLat(pdr.longitude() * K + gnss.longitude() * (1 - K),
                pdr.latitude() * K + gnss.latitude() * (1 - K));

//        fusionPoint = EPSG4326To32650(fusionPoint);
//        Kalmanfilter kalmanfilter = new Kalmanfilter();
//        kalmanfilter.pred(System.currentTimeMillis() / 1000, fusionPoint);
//        kalmanfilter.corr(System.currentTimeMillis() / 1000, fusionPoint);
//        fusionPoint = EPSG32650To4326(fusionPoint);

        return fusionPoint;
    }

    /**
     *
     * @param point 当前点
     * @param mag   当前地磁强度
     * @param dl    变化长度
     * @param dm    变化强度
     * @return      融合点
     */
    public Point indoorFusionLocation(Point point, double mag, double dl, double dm){
        double K = 1.0;

        //分别根据地磁强度和变化率寻找可能的点
        List<Point> ptListByRange = RedisCache.getPtListByRange(mag);
        List<Point> ptListBySlope = RedisCache.getPtListBySlope(dm / dl);

        Point nearestByMag = findNearest(point, ptListByRange);
        Point nearestBySlope = findNearest(point, ptListBySlope);

        if(nearestByMag == null || nearestBySlope == null){
            //当前测量值可能误差比较大，需要分析原因
            Log.w(TAG, "当前测量误差较大");
            return point;
        }

        if(nearestByMag.equals(nearestBySlope) && TurfMeasurement.distance(nearestByMag, point) * 1000 < 3) K = 0.0;


        if(nearestByMag == null) return point;
        Point fusionPoint = Point.fromLngLat(point.longitude() * K + nearestByMag.longitude() * (1 - K),
                point.latitude() * K + nearestByMag.latitude() * (1 - K));

        return fusionPoint;
    }

    public Point EPSG4326To3857(Point point){
        CoordinateTransform _4326To32650 = ctFactory.createTransform(epsg4326, epsg3857);
        ProjCoordinate result = new ProjCoordinate();
        _4326To32650.transform(new ProjCoordinate(point.longitude(), point.latitude()), result);
        return Point.fromLngLat(result.x, result.y);
    }

    public Point EPSG3857To4326(Point point){
        CoordinateTransform _32650To4326 = ctFactory.createTransform(epsg3857, epsg4326);
        ProjCoordinate result = new ProjCoordinate();
        _32650To4326.transform(new ProjCoordinate(point.longitude(), point.latitude()), result);
        return Point.fromLngLat(result.x, result.y);
    }

    public double ButterWorth_lowPass(double v) {
        Butterworth butterworth = new Butterworth();
        butterworth.lowPass(1, 50, 20);
        return butterworth.filter(v);
    }

    public float[] lowPass(float[] input, float[] output) {
        if (output == null) return input;
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + 0.25f * (input[i] - output[i]);
        }
        return output;
    }

    public double calculateCosDistance(ArrayList<Float> l1, ArrayList<Float> l2) {
        double A = 0, B = 0, AB = 0;
        for (int i = 0; i < l2.size(); i++) {
            double a = l1.get(i);
            double b = l2.get(i);
            A += a * a;
            B += b * b;
            AB += a * b;
        }
        return Math.acos(AB / Math.sqrt(A) / Math.sqrt(B));
    }

    public float similarity(double input){
        float result = 0.0f;    //  result is ranges from 0.0 to 1.0;
                                //  1.0 indicates high similarity;

        //Todo: 判断相似度，最长子序列LCSS

        return result;
    }

    public Point findNearest(Point p, List<Point> l) {
        if(l.size() == 0 || p == null) return null;
        Point res = TurfClassification.nearestPoint(p, l);
        return res;
    }

    //Todo: 计算设备所有的轨迹移动线路，匹配出准确率最高的一条，删除偏移较大的轨迹
    public void moveAllRoute(String id, Point p){


        return;
    }

    //Todo: 切换算法
    private boolean isInSwitchingArea(Point p){
        return false;
    }

    private static String calculateWeightedAverageKDistanceLocations(ArrayList<LocationWithDistance> locationWithDistance_Results_List, int K) {
        double LocationWeight = 0.0f;
        double sumWeights = 0.0f;
        double WeightedSumX = 0.0f;
        double WeightedSumY = 0.0f;

        String[] LocationArray = new String[2];
        float x, y;

        int K_Min = K < locationWithDistance_Results_List.size() ? K : locationWithDistance_Results_List.size();

        // Calculate the weighted sum of X and Y
        for (int i = 0; i < K_Min; ++i) {
            if (locationWithDistance_Results_List.get(i).getDistance() != 0.0) {
                LocationWeight = 100000 / Math.pow(locationWithDistance_Results_List.get(i).getDistance(), 2);
            } else {
                LocationWeight = 100;
            }
            LocationArray = locationWithDistance_Results_List.get(i).getLocation().split(" ");

            try {
                x = Float.valueOf(LocationArray[0].trim()).floatValue();
                y = Float.valueOf(LocationArray[1].trim()).floatValue();
            } catch (Exception e) {
                return null;
            }

            sumWeights += LocationWeight;
            WeightedSumX += LocationWeight * x;
            WeightedSumY += LocationWeight * y;
        }

        WeightedSumX /= sumWeights;
        WeightedSumY /= sumWeights;

        return WeightedSumX + " " + WeightedSumY;
    }

    private static String calculateAverageKDistanceLocations(ArrayList<LocationWithDistance> locationWithDistance_Results_List, int K) {
        float sumX = 0.0f;
        float sumY = 0.0f;

        String[] LocationArray;
        float x, y;

        int K_Min = K < locationWithDistance_Results_List.size() ? K : locationWithDistance_Results_List.size();

        // Calculate the sum of X and Y
        for (int i = 0; i < K_Min; ++i) {
            LocationArray = locationWithDistance_Results_List.get(i).getLocation().split(" ");

            try {
                x = Float.valueOf(LocationArray[0].trim()).floatValue();
                y = Float.valueOf(LocationArray[1].trim()).floatValue();
            } catch (Exception e) {
                return null;
            }

            sumX += x;
            sumY += y;
        }

        // Calculate the average
        sumX /= K_Min;
        sumY /= K_Min;

        return sumX + " " + sumY;
    }

    private static String calculateWeightedAverageProbabilityLocations(ArrayList<LocationWithDistance> locationWithDistance_Results_List) {
        double sumProbabilities = 0.0f;
        double WeightedSumX = 0.0f;
        double WeightedSumY = 0.0f;
        double NP;
        float x, y;
        String[] LocationArray;

        for (int i = 0; i < locationWithDistance_Results_List.size(); ++i)
            sumProbabilities += locationWithDistance_Results_List.get(i).getDistance();

        for (int i = 0; i < locationWithDistance_Results_List.size(); ++i) {
            LocationArray = locationWithDistance_Results_List.get(i).getLocation().split(" ");

            try {
                x = Float.valueOf(LocationArray[0].trim()).floatValue();
                y = Float.valueOf(LocationArray[1].trim()).floatValue();
            } catch (Exception e) {
                return null;
            }

            NP = locationWithDistance_Results_List.get(i).getDistance() / sumProbabilities;

            WeightedSumX += (x * NP);
            WeightedSumY += (y * NP);
        }

        return WeightedSumX + " " + WeightedSumY;
    }
}
