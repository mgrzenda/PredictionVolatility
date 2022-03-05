echo starting file=%1 class index=%2 windowSize=%3 binCount=%4 predictionFrequency=%5

java -cp moa.jar -javaagent:sizeofag-1.0.0.jar moa.DoTask EvaluatePrequential  -e (WindowClassificationPerformanceEvaluator -w %3 -e -a -d) -B %4 -K %5 -l (moa.classifiers.bayes.NaiveBayes) -s (ArffFileStream -f data\%1.arff -c %2) -f 100 -d moa_out\%1_%2_%3_%4_%5_stats_NaiveBayes.txt -i 50000 -o moa_out\%1_%2_%3_%4_%5_delayed_raw_prediction_NaiveBayes.txt"

java -cp moa.jar -javaagent:sizeofag-1.0.0.jar moa.DoTask EvaluatePrequential  -e (WindowClassificationPerformanceEvaluator -w %3 -e -a -d)  -B %4 -K %5 -l (meta.AdaptiveRandomForest -m 80) -s (ArffFileStream -f data\%1.arff -c %2) -f 100 -d moa_out\%1_%2_%3_%4_%5_stats_AdaptiveRandomForest.txt -i 50000 -o moa_out\%1_%2_%3_%4_%5_delayed_raw_prediction_AdaptiveRandomForest.txt"

java -cp moa.jar -javaagent:sizeofag-1.0.0.jar moa.DoTask EvaluatePrequential  -e (WindowClassificationPerformanceEvaluator -w %3 -e -a -d) -B %4 -K %5 -l (moa.classifiers.functions.NoChange) -s (ArffFileStream -f data\%1.arff -c %2) -f 100 -d moa_out\%1_%2_%3_%4_%5_stats_NoChange.txt -i 50000 -o moa_out\%1_%2_%3_%4_%5_delayed_raw_prediction_NoChange.txt"

java -cp moa.jar -javaagent:sizeofag-1.0.0.jar moa.DoTask EvaluatePrequential  -e (WindowClassificationPerformanceEvaluator -w %3 -e -a -d)  -B %4 -K %5 -l (moa.classifiers.functions.MajorityClass) -s (ArffFileStream -f data\%1.arff -c %2) -f 100 -d moa_out\%1_%2_%3_%4_%5_stats_MajorityClass.txt -i 50000 -o moa_out\%1_%2_%3_%4_%5_delayed_raw_prediction_MajorityClass.txt"

java -cp moa.jar -javaagent:sizeofag-1.0.0.jar moa.DoTask EvaluatePrequential  -e (WindowClassificationPerformanceEvaluator -w %3 -e -a -d)  -B %4 -K %5 -l (moa.classifiers.lazy.kNN) -s (ArffFileStream -f data\%1.arff -c %2) -f 100 -d moa_out\%1_%2_%3_%4_%5_stats_kNN.txt -i 50000 -o moa_out\%1_%2_%3_%4_%5_delayed_raw_prediction_kNN.txt"

java -cp moa.jar -javaagent:sizeofag-1.0.0.jar moa.DoTask EvaluatePrequential  -e (WindowClassificationPerformanceEvaluator -w %3 -e -a -d)  -B %4 -K %5 -l (moa.classifiers.trees.HoeffdingTree) -s (ArffFileStream -f data\%1.arff -c %2) -f 100 -d moa_out\%1_%2_%3_%4_%5_stats_HoeffdingTree.txt -i 50000 -o moa_out\%1_%2_%3_%4_%5_delayed_raw_prediction_HoeffdingTree.txt"

java -cp moa.jar -javaagent:sizeofag-1.0.0.jar moa.DoTask EvaluatePrequential  -e (WindowClassificationPerformanceEvaluator -w %3 -e -a -d)  -B %4 -K %5 -l (moa.classifiers.trees.HoeffdingAdaptiveTree) -s (ArffFileStream -f data\%1.arff -c %2) -f 100 -d moa_out\%1_%2_%3_%4_%5_stats_HoeffdingAdaptiveTree.txt -i 50000 -o moa_out\%1_%2_%3_%4_%5_delayed_raw_prediction_HoeffdingAdaptiveTree.txt"

