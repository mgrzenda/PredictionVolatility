# Prediction change measures

Repository of a custom MOA version providing support for calculating prediction change measures i.e. measures summarising how predictions from evolving classifiers made for the same instances change over time. This includes the volatility and productive volatility measures. This code extends previously developed code of continuous re-evaluation and can be used to assess the performance of classifiers. 

This code is planned to be available in MOA in the future.

For further details on MOA, see project website: 
http://moa.cms.waikato.ac.nz 


## Key source files
Some of the key changes made to MOA include changes in:
* moa.evaluation.evaluationWindowClassificationPerformanceEvaluator: this version of the evaluator includes newly added code, which accumulates prediction changes for a sliding window of instances
* moa.evaluation.BasicClassificationPerformanceEvaluator: this version of the evaluator includes newly added code, which uses label change tuples to track changes in predicted labels


## Sample execution
For sample input data prepared to trigger initial and final predictions under fixed and varied label latency, please refer to [data](./data) folder

For sample scripts starting processing in MOA aimed at calculating volatility and productive volatility, please refer to [scripts](/.scripts) folder