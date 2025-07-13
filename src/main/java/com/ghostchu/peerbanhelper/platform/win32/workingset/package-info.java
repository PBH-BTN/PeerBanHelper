/**
 * Windows 工作集管理模块
 * 
 * <p>此模块提供了在Windows平台上管理进程工作集的功能，允许将进程的不常用部分
 * 存入操作系统的虚拟内存（分页文件）中，从而减少物理内存的使用。</p>
 * 
 * <h2>主要功能</h2>
 * <ul>
 *   <li>清空进程工作集，将不常用页面移至分页文件</li>
 *   <li>设置进程工作集大小限制</li>
 *   <li>内存压缩和整理</li>
 *   <li>内存使用监控</li>
 * </ul>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link com.ghostchu.peerbanhelper.platform.win32.workingset.WindowsWorkingSetManager} - 主要的工作集管理器</li>
 *   <li>{@link com.ghostchu.peerbanhelper.platform.win32.workingset.WorkingSetManagerFactory} - 工厂类，提供便捷接口</li>
 *   <li>{@link com.ghostchu.peerbanhelper.platform.win32.workingset.MemoryMonitor} - 内存监控工具</li>
 *   <li>{@link com.ghostchu.peerbanhelper.platform.win32.workingset.Kernel32} - Windows API接口定义</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 检查平台支持
 * if (WorkingSetManagerFactory.isSupported()) {
 *     // 简单的内存整理
 *     boolean result = WorkingSetManagerFactory.trimMemory();
 *     
 *     // 内存压缩
 *     boolean compressResult = WorkingSetManagerFactory.compressMemory(8 * 1024 * 1024);
 *     
 *     // 获取管理器实例进行更精细的控制
 *     WindowsWorkingSetManager manager = WorkingSetManagerFactory.getInstance();
 *     if (manager != null) {
 *         manager.emptyWorkingSet();
 *         manager.setWorkingSetSize(minSize, maxSize);
 *     }
 * }
 * }</pre>
 * 
 * <h2>技术说明</h2>
 * <p>此模块使用JNA（Java Native Access）来调用Windows API，主要使用以下API：</p>
 * <ul>
 *   <li><code>EmptyWorkingSet</code> - 清空进程工作集</li>
 *   <li><code>SetProcessWorkingSetSize</code> - 设置进程工作集大小限制</li>
 *   <li><code>GetCurrentProcess</code> - 获取当前进程句柄</li>
 * </ul>
 * 
 * <h2>注意事项</h2>
 * <ul>
 *   <li>此功能仅在Windows平台可用</li>
 *   <li>需要适当的进程权限</li>
 *   <li>过频繁的调用可能影响性能</li>
 *   <li>建议根据内存使用情况智能调用</li>
 * </ul>
 * 
 * @author PeerBanHelper
 * @since 1.0
 */
package com.ghostchu.peerbanhelper.platform.win32.workingset;
