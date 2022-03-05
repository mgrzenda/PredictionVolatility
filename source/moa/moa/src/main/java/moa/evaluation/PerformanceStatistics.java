/*
 *    PerformanceStatistics.java
 *    Copyright (C) 2020 Warsaw University of Technology, Warszawa, Poland
 *    @author Maciej Grzenda (M.Grzenda@mini.pw.edu.pl)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *    
 */

package moa.evaluation;

import moa.core.Measurement;

public interface PerformanceStatistics {

    /**
     * Gets the current measurements monitored by this evaluator.
     *
     * @return an array of measurements monitored by this evaluator
     */
	public Measurement[] getPerformanceMeasurements();
	
	public void setEvaluatorInstance(String string);
	public String getEvaluatorInstance();
}
