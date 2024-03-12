Preliminaries:

-  make sure, to install GNU-octave (http://www.gnu.org/software/octave/download.html)
   and ensure that the binary is added to your system's PATH variable

Step-by-step tutorial:   
1. Run provided launch configuration "ProM with UITopia (RepairLog)"

2. Get the stochastic Petri net:

  2.1. import a log and a corresponding Petri net into ProM (log has to have the "time:timestamp" attribute for it's events)
       -> IMPORTANT: Make sure, that network does not contain race conditions and splits/joins parallel branches with immediate transitions!
       
  2.2. Perform the plug-in "Replay Log on Petri Net for PerformanceAnalysis" (standard settings)
       (be sure to set visibility of transitions and initial/final markings on the Petri net)
       choose: "Yes, only reliable results"
       
  2.3. Use that Manifest output of 2.2. as input to the plug-in "Enrich Petri Net model with stochastic performance data"
       You can choose which distribution type you want to get in the resulting stochastic Petri net.
       - NORMAL: assumes normal distributions with mean durations and variances of the samples
       - EXPONENTIAL: assumes exponential distributions with a mean firing rate
       - GAUSSIAN_KERNEL: non-parametric estimation with gaussian kernels 
                         (this is comparable to a smoothed version of a histogram)


3. Repair a log:
 
  3.1. select plug-in "Repair Log with stochastic Petri net"
       - choose the stochastic Petri net from 2.3.
       - Select a log to repair according to the stochastic Petri net
         
  3.2. in the options dialog:
       1. chance of event missing in the log: probability that an event was forgotten. 
         (this penalizes traces, where more artificial events would have to be inserted than in the others)               
       2. the slider weights the ratio of the costs for:
           structural fitness (alignments with choosing more frequent branches have higher probability)
       &   insertion cost (alignments with less inserted activities have higher probability
       3. time unit in the stochastic model: we allow to have different units in the model than in the log (always milliseconds, there)
       4. Number of parallel workers. Each worker has a thread and an own octave instance and the log will be split over the workers to speed up computation
          
4. Evaluate how well repair worked: (if you repaired a trace, where you know, when which activity happened, you can compare the repaired events with the original ones)
  4.1. call "Evaluate Repaired Log" plug-in with:
      - original log, 
      - repaired log,
      - Petri net containing the structure.
       


