# Windows 工作集管理模块

此模块提供了在Windows平台上管理进程工作集的功能，允许将进程的不常用部分存入操作系统的虚拟内存（分页文件）中，从而减少物理内存的使用。

## 功能特性

- ✅ **清空工作集**：将不常用的内存页面移至分页文件
- ✅ **设置工作集限制**：控制进程的最小/最大工作集大小
- ✅ **内存压缩**：强制性地减少内存占用
- ✅ **内存监控**：监控当前内存使用情况
- ✅ **平台检测**：自动检测是否为Windows平台
- ✅ **智能整理**：基于内存使用情况的智能整理建议

## 核心类说明

### 1. `WindowsWorkingSetManager`
主要的工作集管理器，提供核心功能：
- `emptyWorkingSet()` - 清空工作集
- `setWorkingSetSize()` - 设置工作集大小限制
- `compressMemory()` - 执行内存压缩
- `trimMemory()` - 轻量级内存整理

### 2. `WorkingSetManagerFactory`
工厂类，提供便捷的静态方法：
- `getInstance()` - 获取管理器实例
- `isSupported()` - 检查平台支持
- `trimMemory()` - 便捷内存整理
- `compressMemory()` - 便捷内存压缩

### 3. `MemoryMonitor`
内存监控工具：
- `getMemorySummary()` - 获取内存使用摘要
- `shouldTrimMemory()` - 建议是否执行内存整理
- `formatBytes()` - 格式化字节数显示

### 4. `Kernel32`
Windows API接口定义，使用JNA调用系统API。

## 使用示例

### 基本使用

```java
import com.ghostchu.peerbanhelper.platform.win32.workingset.*;

// 检查平台支持
if (WorkingSetManagerFactory.isSupported()) {
    // 简单的内存整理
    boolean result = WorkingSetManagerFactory.trimMemory();
    System.out.println("内存整理结果: " + (result ? "成功" : "失败"));
    
    // 内存压缩（目标8MB）
    boolean compressResult = WorkingSetManagerFactory.compressMemory(8 * 1024 * 1024);
    System.out.println("内存压缩结果: " + (compressResult ? "成功" : "失败"));
} else {
    System.out.println("当前平台不支持工作集管理");
}
```

### 高级使用

```java
// 获取管理器实例
WindowsWorkingSetManager manager = WorkingSetManagerFactory.getInstance();
if (manager != null) {
    // 设置工作集大小限制
    long minSize = 16 * 1024 * 1024; // 16MB
    long maxSize = 128 * 1024 * 1024; // 128MB
    boolean setResult = manager.setWorkingSetSize(minSize, maxSize);
    
    // 清空工作集
    boolean emptyResult = manager.emptyWorkingSet();
    
    // 恢复自动管理
    boolean autoResult = manager.setAutoManageWorkingSet();
}
```

### 内存监控

```java
MemoryMonitor monitor = new MemoryMonitor();

// 获取内存使用摘要
String summary = monitor.getMemorySummary();
System.out.println(summary);

// 检查是否需要内存整理
if (monitor.shouldTrimMemory()) {
    System.out.println("建议执行内存整理");
    WorkingSetManagerFactory.trimMemory();
}

// 格式化内存大小
long workingSetSize = monitor.getEstimatedWorkingSetSize();
System.out.println("估算工作集大小: " + MemoryMonitor.formatBytes(workingSetSize));
```

### 定期内存整理

```java
// 创建定期内存整理任务
WorkingSetUsageExample.PeriodicMemoryTrimTask task = 
    new WorkingSetUsageExample.PeriodicMemoryTrimTask(300000); // 5分钟间隔

// 在新线程中运行
Thread trimThread = new Thread(task);
trimThread.setDaemon(true);
trimThread.start();
```

## Windows API 说明

此模块主要使用以下Windows API：

1. **`EmptyWorkingSet`**
   - 功能：将进程的工作集最小化，强制将不活跃页面移至分页文件
   - 效果：减少物理内存使用，但可能影响性能

2. **`SetProcessWorkingSetSize`**
   - 功能：设置进程的最小和最大工作集大小
   - 参数：`-1` 表示让系统自动管理

3. **`GetCurrentProcess`**
   - 功能：获取当前进程的句柄

## 使用场景

### 适合使用的场景：
- 🎯 应用程序长时间运行但暂时不活跃
- 🎯 内存使用较高需要临时释放
- 🎯 在低内存环境下运行
- 🎯 后台服务需要减少内存占用
- 🎯 系统资源紧张时的优化

### 不适合使用的场景：
- ❌ 频繁访问内存的应用
- ❌ 对性能要求极高的实时应用
- ❌ 短时间运行的程序
- ❌ 非Windows平台

## 注意事项

1. **性能影响**：清空工作集后，下次访问被移出的内存页面会有页面错误，可能影响性能
2. **频率控制**：不建议过于频繁调用，建议根据内存使用情况智能决策
3. **权限要求**：某些操作可能需要管理员权限
4. **平台限制**：仅支持Windows平台
5. **JNA依赖**：确保项目包含JNA和JNA-Platform依赖

## 错误处理

模块提供了完善的错误处理和日志记录：

```java
try {
    boolean result = manager.emptyWorkingSet();
    if (!result) {
        // 检查日志了解具体错误原因
        logger.warn("工作集清空失败，请查看详细日志");
    }
} catch (Exception e) {
    // 处理异常
    logger.error("执行工作集操作时发生异常", e);
}
```

## 完整示例

参考 `WorkingSetUsageExample` 类了解完整的使用示例和最佳实践。

## 技术细节

- **JNA版本**：使用项目中配置的JNA版本
- **日志框架**：使用SLF4J进行日志记录
- **线程安全**：工厂类使用双重检查锁定确保线程安全
- **异常处理**：提供专门的异常类 `WorkingSetException`
