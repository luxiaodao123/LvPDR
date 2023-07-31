package com.example.lvpdr;

import com.github.psambit9791.jdsp.signal.peaks.FindPeak;
import com.github.psambit9791.jdsp.signal.peaks.Peak;
import com.mapbox.geojson.Point;

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

import java.util.ArrayList;


public class CoreAlgorithm {
    private  static  ArrayList<float[]> accelerationData = new ArrayList<float[]>();
    private CRSFactory crsFactory = new CRSFactory();
    private CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
    final  private  CoordinateReferenceSystem epsg4326 = crsFactory.createFromName("epsg:4326");
    final  private CoordinateReferenceSystem epsg3857 = crsFactory.createFromName("epsg:3857");
    private double sampleRate;
    private double height;
    private double K;  //Coefficient (may need to be adjusted for different individuals)

    public CoreAlgorithm(double sampleRate, double height, double K) {
        this.sampleRate = sampleRate;
        this.height = height;
        this.K = K;

       // double [] testdata = {6.6, 14, 13.8, 17, 16.5, 16.9, 16.7, 10.5, 7, 7.1, 6.5, 6.4, 6.2,6.3,6.5};
    }

    class ButterworthFilter {
        private double[] xv, yv;
        private double gain;
        private double[] numeratorCoeffs, denominatorCoeffs;

        public ButterworthFilter(double cutoffFrequency, double sampleRate, int order) {
            //sampleRate代表采样频率
            //order表示巴特沃斯滤波器的阶数。阶数决定了滤波器的复杂度和性能
            xv = new double[order + 1];
            yv = new double[order + 1];

            // Calculate the filter coefficients
            double[] coeffs = calculateCoefficients(cutoffFrequency, sampleRate, order);
            numeratorCoeffs = new double[order + 1];
            denominatorCoeffs = new double[order + 1];
            for (int i = 0; i < order + 1; i++) {
                numeratorCoeffs[i] = coeffs[i];
                denominatorCoeffs[i] = coeffs[order + 1 + i];
            }
        }

        public double filter(double value) {
            // Shift the old values
            for (int i = 0; i < xv.length - 1; i++) {
                xv[i] = xv[i + 1];
                yv[i] = yv[i + 1];
            }

            // Add the new value
            xv[xv.length - 1] = value / gain;

            // Calculate the filter output
            yv[yv.length - 1] = 0;
            for (int i = 0; i < xv.length; i++) {
                yv[yv.length - 1] += numeratorCoeffs[i] * xv[xv.length - i - 1];
                if (i > 0) {
                    yv[yv.length - 1] -= denominatorCoeffs[i] * yv[yv.length - i - 1];
                }
            }

            return yv[yv.length - 1];
        }

        private double[] calculateCoefficients(double cutoffFrequency, double sampleRate, int order) {
            // Calculate the normalized cutoff frequency
            double omega = Math.tan(Math.PI * cutoffFrequency / sampleRate);

            // Calculate the filter coefficients
            int n = order / 2;
            int m = order - n * 2;
            int size = n * 2 + m;
            double[] a = new double[size + 1];
            a[0] = 1;
            for (int k = 0; k < n; k++) {
                double[] aTmp = new double[size + 1];
                aTmp[0] = a[0];
                for (int i = 0; i < size; i++) {
                    aTmp[i + 1] += a[i];
                    aTmp[i] += a[i + 1];
                }
                a = aTmp;
            }
            if (m > 0) {
                for (int i = size; i >= 0; i--) {
                    a[i + m] += a[i];
                    if (i > m) {
                        a[i] += a[i - m];
                    }
                }
            }
            for (int k = n - 1; k >= 0; k--) {
                for (int i = size; i >= m; i--) {
                    a[i] -= a[i - m];
                    if (i > m) {
                        a[i] -= a[i - m - 1];
                    }
                }
                for (int i = size; i >= m; i--) {
                    if (i > m) {
                        a[i - m - 1] += a[i];
                    }
                    if (i > m * 2) {
                        a[i - m * 2 - 1] += a[i];
                    }
                }
            }

            // Calculate the gain
            gain = Math.pow(omega, order);
            for (int k = size; k >= m; k--) {
                gain += omega * omega * a[k];
                if (k > m) {
                    gain += omega * omega * a[k - m];
                }
            }

            // Calculate the filter coefficients
            double[] coeffs = new double[(order + 1) * 2];
            for (int i = 0; i < order + 1; i++) {
                coeffs[i] = a[i];
                coeffs[order + 1 + i] = a[i];
            }
            return coeffs;
        }
    }

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
                K = P * HT * S-1       ← S-1 is the matrix inverse of S
                y = m - (H * x)
                x = x + (K * y)
                P = (I - (K * H)) * P       ← I is the Identity matrix
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

//    public double calculateStepLength(ArrayList<float[]> accelerationData) {
//        // Calculate the magnitude of the acceleration vector
//        double[] accelerationMagnitude = new double[accelerationData.size()];
//        int index = 0;
//        for (float[] acc: accelerationData) {
//            accelerationMagnitude[index++] = Math.sqrt(Math.pow(acc[0], 2) + Math.pow(acc[1], 2) + Math.pow(acc[2], 2));
//        }
//
//        // High-pass filter the acceleration magnitude to remove gravity
//        double fc = 0.5;  // Cutoff frequency
//        ButterworthFilter butterworthFilter = new ButterworthFilter(fc, sampleRate, 4);
//        double[] filteredAccelerationMagnitude = new double[accelerationMagnitude.length];
//        for (int i = 0; i < accelerationMagnitude.length; i++) {
//            filteredAccelerationMagnitude[i] = butterworthFilter.filter(accelerationMagnitude[i]);
//        }
//
//        // Find peaks in the filtered acceleration magnitude
//        int[] peakIndices = peaks(accelerationMagnitude);
//
//        // Calculate the average time between peaks
//        DescriptiveStatistics stats = new DescriptiveStatistics();
//        for (int i = 1; i < peakIndices.length; i++) {
//            stats.addValue(peakIndices[i] - peakIndices[i - 1]);
//        }
//        double timeBetweenPeaks = stats.getMean() / sampleRate;
//        System.out.println(stats.getMean());
//
//        // Calculate step frequency
//        double stepFrequency = 1 / timeBetweenPeaks;
//        if(stepFrequency < 0) return 0;
//
//        // Calculate step length using the formula: L = K * H * sqrt(f),
//        // where L is step length, K is a coefficient, H is height and f is step frequency
//        double stepLength = K * height * Math.sqrt(stepFrequency);
//
//        return stepLength;
//    }

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
        return -0.19 * diff + 0.94 * Math.sqrt(Math.sqrt(diff));
    }

//    private int[] peaks(double[] data){
//        int n = data.length;
//        double[] real = new double[n];
//        double[] imag = new double[n];
//        for (int i = 0; i < n; i++) {
//            real[i] = data[i];
//            imag[i] = 0;
//        }
//        fft(real, imag);
//        double[] magnitude = new double[n];
//        for (int i = 0; i < n; i++) {
//            magnitude[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
//        }
//        return findPeaks(magnitude);
//    }

    public void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n == 1) return;

        double[] evenReal = new double[n / 2];
        double[] evenImag = new double[n / 2];
        double[] oddReal = new double[n / 2];
        double[] oddImag = new double[n / 2];

        for (int i = 0; i < n / 2; i++) {
            evenReal[i] = real[2 * i];
            evenImag[i] = imag[2 * i];
            oddReal[i] = real[2 * i + 1];
            oddImag[i] = imag[2 * i + 1];
        }

        fft(evenReal, evenImag);
        fft(oddReal, oddImag);

        for (int k = 0; k < n / 2; k++) {
            double tReal = evenReal[k];
            double tImag = evenImag[k];
            double angle = -2 * Math.PI * k / n;
            double cosAngle = Math.cos(angle);
            double sinAngle = Math.sin(angle);
            real[k] = tReal + cosAngle * oddReal[k] - sinAngle * oddImag[k];
            imag[k] = tImag + cosAngle * oddImag[k] + sinAngle * oddReal[k];
            real[k + n / 2] = tReal - cosAngle * oddReal[k] + sinAngle * oddImag[k];
            imag[k + n / 2] = tImag - cosAngle * oddImag[k] - sinAngle * oddReal[k];
        }
    }

//    private int[] findPeaks(double[] data) {
//        // Find peaks in data using the Fast Fourier Transform (FFT)
//        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
//        Complex[] complexData = new Complex[data.length];
//        for (int i = 0; i < data.length; i++) {
//            complexData[i] = new Complex(data[i], 0);
//        }
//        Complex[] fftData = fft.transform(complexData, TransformType.FORWARD);
//
//        // Find peak indices
//        int[] peakIndices = new int[fftData.length];
//        int peakCount = 0;
//        for (int i = 1; i < fftData.length - 1; i++) {
//            if (fftData[i].abs() > fftData[i - 1].abs() && fftData[i].abs() > fftData[i + 1].abs()) {
//                peakIndices[peakCount] = i;
//                peakCount++;
//            }
//        }
//
//        return peakIndices;
//    }

    private int[] peakDetections(double[] data) {
        FindPeak fp = new FindPeak(data);

        Peak out = fp.detectPeaks();
        int[] peaks = out.getPeaks();

        Peak out2 = fp.detectTroughs();
        int[] troughs = out2.getPeaks();
        return troughs;
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
                continue;
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

}
