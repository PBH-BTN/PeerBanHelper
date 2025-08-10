/*
 * WarmRoast
 * Copyright (C) 2013 Albert Pham <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.warmroast;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;

@Slf4j
@Component
public class WarmRoast extends TimerTask {

    private final int interval;
    private final VirtualMachine vm;
    private final Timer timer = new Timer("Roast Pan", true);
    // private final McpMapping mapping = new McpMapping();
    private final SortedMap<String, StackNode> nodes = new TreeMap<>();
    private JMXConnector connector;
    private MBeanServerConnection mbsc;
    private ThreadMXBean threadBean;
    private String filterThread;
    private long endTime = -1;

    public WarmRoast(VirtualMachine vm, int interval) {
        this.vm = vm;
        this.interval = interval;
    }

    public Map<String, StackNode> getData() {
        return nodes;
    }

    private StackNode getNode(String name) {
        StackNode node = nodes.get(name);
        if (node == null) {
            node = new StackNode(name);
            nodes.put(name, node);
        }
        return node;
    }

    public String getFilterThread() {
        return filterThread;
    }

    public void setFilterThread(String filterThread) {
        this.filterThread = filterThread;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long l) {
        this.endTime = l;
    }

    public void connect()
            throws IOException, AgentLoadException, AgentInitializationException {
        // Load the agent
        String connectorAddr = vm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
        if (connectorAddr == null) {
            String agent = vm.getSystemProperties().getProperty("java.home") + File.separator + "lib" + File.separator + "management-agent.jar";
            vm.loadAgent(agent);
            connectorAddr = vm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
        }

        // Connect
        JMXServiceURL serviceURL = new JMXServiceURL(connectorAddr);
        connector = JMXConnectorFactory.connect(serviceURL);
        mbsc = connector.getMBeanServerConnection();
        try {
            threadBean = getThreadMXBean();
        } catch (MalformedObjectNameException e) {
            throw new IOException("Bad MX bean name", e);
        }
    }

    private ThreadMXBean getThreadMXBean()
            throws IOException, MalformedObjectNameException {
        ObjectName objName = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
        Set<ObjectName> mbeans = mbsc.queryNames(objName, null);
        for (ObjectName name : mbeans) {
            return ManagementFactory.newPlatformMXBeanProxy(
                    mbsc, name.toString(), ThreadMXBean.class);
        }
        throw new IOException("No thread MX bean found");
    }

    @Override
    public synchronized void run() {
        if (endTime >= 0) {
            if (endTime <= System.currentTimeMillis()) {
                cancel();
                log.debug("Stopped sampling after reaching end time: {}", endTime);
                return;
            }
        }

        ThreadInfo[] threadDumps = threadBean.dumpAllThreads(false, false);
        for (ThreadInfo threadInfo : threadDumps) {
            String threadName = threadInfo.getThreadName();
            StackTraceElement[] stack = threadInfo.getStackTrace();

            if (threadName == null || stack == null) {
                continue;
            }

            if (filterThread != null && !filterThread.equals(threadName)) {
                continue;
            }

            StackNode node = getNode(threadName);
            node.log(stack, interval);
        }
    }

    public void start() {
        timer.scheduleAtFixedRate(this, interval, interval);
    }


}
