/**
 * *****************************************************************************
 * @ Year 2013 This is the source code of the following papers.
 * 
* 1) Geocrowd: A Server-Assigned Crowdsourcing Framework. Hien To, Leyla
 * Kazemi, Cyrus Shahabi.
 * 
*
 * Please contact the author Hien To, ubriela@gmail.com if you have any
 * question.
 * 
* Contributors: Hien To - initial implementation
******************************************************************************
 */
package org.geocrowd.setcover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.geocrowd.common.Constants;

// TODO: Auto-generated Javadoc
/**
 * The Class SetCoverGreedyWaitTillDeadline.
 *
 * @author Luan
 */
public class SetCoverGreedy_LargeTaskCoverage extends SetCoverGreedy {

    /**
     * The k.
     */
    Integer k = 4;

    /**
     * The average tasks per worker.
     */
    public double averageTasksPerWorker;

    /**
     * The average workers per task.
     */
    public double averageWorkersPerTask;

    public SetCoverGreedy_LargeTaskCoverage(ArrayList container, Integer current_time_instance) {
        super(container, current_time_instance);
    }

    /*
     Check worker contain elemenst will not available at next time
     */
    /**
     * Contain element dead at next time.
     *
     * @param s the s
     * @param current_time_instance the current_time_instance
     * @return true, if successful
     */
    private boolean containElementDeadAtNextTime(HashMap<Integer, Integer> s,
            int current_time_instance) {
        return s.values().contains(current_time_instance + 1) || (current_time_instance == Constants.TIME_INSTANCE - 1);
    }

    /**
     * Greedy algorithm.
     *
     * @return number of assigned workers
     */
    public HashSet<Integer> minSetCover() {
        ArrayList<HashMap<Integer, Integer>> S = (ArrayList<HashMap<Integer, Integer>>) listOfSets.clone();
        HashSet<Integer> Q = (HashSet<Integer>) universe.clone();
//        HashSet<Integer> C = new HashSet<Integer>();
        assignedTaskSet = new HashSet<Integer>();

//        ArrayList<HashMap<Integer, Integer>> AW = new ArrayList<>();
//        int totalTasks = 0;
//        int totalAssignedWorkers = 0;
        int set_size = S.size();

        while (!Q.isEmpty()) {
            HashMap<Integer, Integer> maxSet = null;
            int maxElem = 0;
            for ( int o=0;o<S.size();o++) {
            	HashMap<Integer, Integer> s = S.get(o);
                //
                // select the item set that maximize coverage
                // how many elements in s that are not in C
                int newElem = 0;
                for (Integer i : s.keySet()) {
                    if (!assignedTaskSet.contains(i)) {
                        newElem++;
                    }
                }
                if (newElem > maxElem
                        && (newElem >= k || containElementDeadAtNextTime(s, currentTimeInstance))) // check condition: only select workers that either cover at least K (e.g., k=2,3..)
                //tasks or cover any task that will not available in the next time instance
                {
                    maxElem = newElem;
                    maxSet = s;
                    assignWorkers.add(o);
                }
            }
            if (maxSet == null) {
                break;
            }

            //update total task 
//            totalTasks += maxSet.size();
//            AW.add(maxSet);
            S.remove(maxSet);
            Q.removeAll(maxSet.keySet());

            Set assignedSet = maxSet.keySet();
            for (Object kt : assignedSet) {
                Integer key = (Integer)kt;
                if (!assignedTaskSet.contains(key)) {
                    
                    averageDelayTime += currentTimeInstance - (maxSet.get(key) - Constants.TaskDuration) + 1;
                    assignedTaskSet.add(key);
                }
            }

        }

        assignedTasks = assignedTaskSet.size();
//        averageTime = averageTime*1.0/assignedTasks;
        System.out.println("#Task assigned: " + assignedTasks);
        return assignWorkers;
    }
}
