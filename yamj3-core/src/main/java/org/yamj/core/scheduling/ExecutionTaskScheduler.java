/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.scheduling;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yamj.core.database.model.ExecutionTask;
import org.yamj.core.service.tasks.ExecutionTaskService;

@Component
public class ExecutionTaskScheduler {

    private static final ReentrantLock EXECUTION_TASK_LOCK = new ReentrantLock();
    
    @Autowired
    private ExecutionTaskService executionTaskService;
    
    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void executeTasks() {
        if (EXECUTION_TASK_LOCK.tryLock()) {
            try {
                for (ExecutionTask task : this.executionTaskService.getExecutableTasks()) {
                    executionTaskService.executeTask(task);
                }
            } finally {
                EXECUTION_TASK_LOCK.unlock();
            }
        }
    }
}