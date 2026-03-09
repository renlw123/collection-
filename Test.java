package java.util.concurrent;

import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import jdk.internal.misc.Unsafe;


public class ConcurrentHashMap<K,V> extends AbstractMap<K,V>
        implements ConcurrentMap<K,V>, Serializable {
    private static final long serialVersionUID = 7249069246763182397L;

    /* ---------------- Constants -------------- */

    /**
     * 最大可能的表容量。这个值必须是1<<30，以确保在Java数组分配和索引范围内使用2的幂次方表大小，
     * 另外还需要这个值是因为32位哈希字段的高两位用于控制目的。
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;// 最大容量 2^30

    /**
     * 默认的初始表容量。必须是2的幂次方（至少为1）且不超过MAXIMUM_CAPACITY。
     */
    private static final int DEFAULT_CAPACITY = 16;// 默认初始容量为16

    /**
     * 最大可能的数组大小（不要求是2的幂次方）。
     * toArray和相关方法需要这个值。
     */
    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;// 最大数组大小 Integer最大值减8

    /**
     * 此表的默认并发级别。未使用，但为了与此类以前的版本兼容而定义。
     */
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;// 默认并发级别16

    /**
     * 此表的负载因子。构造函数中覆盖此值仅影响初始表容量。
     * 通常不使用实际的浮点值——使用诸如n - (n >>> 2)的表达式更简单，用于相关的扩容阈值。
     */
    private static final float LOAD_FACTOR = 0.75f; // 负载因子0.75

    /**
     * 使用树而不是链表作为箱子的阈值。当向至少有这么多节点的箱子添加元素时，
     * 箱子会被转换为树。该值必须大于2，且应至少为8，以便与树移除时关于收缩时转换回普通箱子的假设相匹配。
     */
    static final int TREEIFY_THRESHOLD = 8;// 链表转红黑树的阈值8

    /**
     * 在扩容操作期间对（拆分后的）箱子进行反树化的阈值。应小于TREEIFY_THRESHOLD，
     * 且最多为6，以便与移除时的收缩检测相匹配。
     */
    static final int UNTREEIFY_THRESHOLD = 6;// 红黑树转链表的阈值6

    /**
     * 箱子可以被树化的最小表容量。（否则，如果箱子中节点太多，表会进行扩容）
     * 该值应至少为4 * TREEIFY_THRESHOLD，以避免扩容和树化阈值之间的冲突。
     */
    static final int MIN_TREEIFY_CAPACITY = 64;// 链表转红黑树的最小表容量64

    /**
     * 每次传输步骤的最小重新分配次数。范围被细分以允许多个扩容线程。
     * 此值作为下限，以避免扩容线程遇到过多的内存争用。
     * 该值应至少为DEFAULT_CAPACITY。
     */
    private static final int MIN_TRANSFER_STRIDE = 16;// 最小传输步长16

    /**
     * 用于sizeCtl中生成标记的位数。对于32位数组必须至少为6。
     */
    private static final int RESIZE_STAMP_BITS = 16;// 扩容标记位数16

    /**
     * 可以帮助扩容的最大线程数。必须能放入32 - RESIZE_STAMP_BITS位中。
     */
    private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1;// 最大扩容线程数 2^16-1

    /**
     * 在sizeCtl中记录扩容标记的位移量。
     */
    private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;// 扩容标记位移量16

    /*
     * Encodings for Node hash fields. See above for explanation.
     */
    static final int MOVED     = -1; // 转发节点的哈希值（表示节点正在迁移）
    static final int TREEBIN   = -2; // 树根节点的哈希值（表示这是一个红黑树）
    static final int RESERVED  = -3; // 临时保留节点的哈希值
    static final int HASH_BITS = 0x7fffffff; // 普通节点哈希的可用位（用于消除符号位）

    /** Number of CPUS, to place bounds on some sizings */
    static final int NCPU = Runtime.getRuntime().availableProcessors();// 可用的处理器数量

    /**
     * 序列化的伪字段，仅为jdk7兼容性提供。
     * @serialField segments Segment[]
     *   段，每个都是一个专门的哈希表。
     * @serialField segmentMask int
     *   用于索引段的掩码值。键哈希码的高位用于选择段。
     * @serialField segmentShift int
     *   段内索引的位移值。
     */
    private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("segments", Segment[].class),// segments字段
            new ObjectStreamField("segmentMask", Integer.TYPE),// segmentMask字段
            new ObjectStreamField("segmentShift", Integer.TYPE),// segmentShift字段
    };

    /* ---------------- Nodes -------------- */

    /**
     * 键值条目。此类从不作为用户可变的Map.Entry导出（即支持setValue；见下面的MapEntry），
     * 但可用于批量任务中使用的只读遍历。
     * 具有负哈希字段的Node子类是特殊的，并包含空的键和值（但从不导出）。
     * 否则，键和值从不为null。
     */
    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;// 节点的哈希值
        final K key;// 节点的键
        volatile V val;// 节点的值
        volatile Node<K,V> next;// 指向下一个节点的引用

        // 构造函数
        Node(int hash, K key, V val) {
            this.hash = hash;
            this.key = key;
            this.val = val;
        }

        // 带next的构造函数
        Node(int hash, K key, V val, Node<K,V> next) {
            this(hash, key, val);
            this.next = next;
        }

        public final K getKey()     { return key; }
        public final V getValue()   { return val; }
        public final int hashCode() { return key.hashCode() ^ val.hashCode(); }
        public final String toString() {
            return Helpers.mapEntryToString(key, val);
        }
        public final V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        public final boolean equals(Object o) {
            Object k, v, u; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    (k == key || k.equals(key)) &&
                    (v == (u = val) || v.equals(u)));
        }

        /**
         * Virtualized support for map.get(); overridden in subclasses.
         */
        Node<K,V> find(int h, Object k) {// 根据哈希和键查找节点
            Node<K,V> e = this;
            if (k != null) {// 如果键不为空
                do {// 遍历链表
                    K ek;
                    if (e.hash == h &&// 哈希相等
                            ((ek = e.key) == k || (ek != null && k.equals(ek))))// 键相等
                        return e;// 返回找到的节点
                } while ((e = e.next) != null);// 移动到下一个节点
            }
            return null;// 没找到目标节点返回null
        }
    }

    /* ---------------- Static utilities -------------- */

    // 将哈希值进行扰动计算，减少哈希冲突（散列算法）
    static final int spread(int h) {
        // 将高16位与低16位异或，然后与HASH_BITS（0x7fffffff）相与，保证结果为正数
        // 散列算法的本质就是让key的hashCode值的高低位做异或运算，让高位也参与到计算索引位置的过程中，从而减少hash冲突
        return (h ^ (h >>> 16)) & HASH_BITS;
    }

    /**
     * 返回给定期望容量的2的幂次方表大小。
     */
    private static final int tableSizeFor(int c) {
        // 计算大于等于c的最小2的幂次方
        int n = -1 >>> Integer.numberOfLeadingZeros(c - 1);// 将c-1的最高位1之后的所有位都设为1
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;// 返回2的幂次方
    }

    /**
     * 如果x的形式是"class C implements Comparable<C>"，则返回x的Class，否则返回null。
     * 用于判断对象是否可以进行比较排序
     */
    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {// 如果实现了Comparable接口
            Class<?> c; Type[] ts, as; ParameterizedType p;
            if ((c = x.getClass()) == String.class) // 如果是String类型，直接返回
                return c;// 返回String类型
            if ((ts = c.getGenericInterfaces()) != null) {// 获取该类实现的泛型接口
                for (Type t : ts) {// 遍历所有接口
                    if ((t instanceof ParameterizedType) &&// 如果是参数化类型
                            ((p = (ParameterizedType)t).getRawType() ==
                                    Comparable.class) &&// 原始类型是Comparable
                            (as = p.getActualTypeArguments()) != null &&// 获取类型参数
                            as.length == 1 && as[0] == c) // 类型参数是当前类本身
                        return c;// 返回当前类本身
                }
            }
        }
        return null;// 不满足条件返回null
    }

    /**
     * 如果x匹配kc（k的筛选后的可比较类），则返回k.compareTo(x)，否则返回0。
     */
    @SuppressWarnings({"rawtypes","unchecked"}) // for cast to Comparable
    static int compareComparables(Class<?> kc, Object k, Object x) {
        // 如果x为空或者x的类型与kc不同，返回0；否则调用compareTo进行比较
        return (x == null || x.getClass() != kc ? 0 :
                ((Comparable)k).compareTo(x));
    }

    /* ---------------- Table element access -------------- */

    /*
     * 原子访问方法用于表元素以及扩容过程中正在构建的下一个表的元素。
     * 所有对tab参数的使用必须由调用者进行null检查。
     * 所有调用者还偏执地预先检查tab的长度不为0（或等效检查），
     * 从而确保任何以哈希值与(length - 1)相与形式的索引参数都是有效索引。
     * 注意，为了正确应对用户的任意并发错误，这些检查必须在局部变量上操作，
     * 这解释了一些看起来奇怪的內联赋值。
     * 注意对setTabAt的调用总是在锁定区域内发生，因此只需要释放顺序。
     */

    // 获取table中索引i处的节点，使用volatile读保证可见性
    @SuppressWarnings("unchecked")
    static final <K,V> Node<K,V> tabAt(Node<K,V>[] tab, int i) {
        return (Node<K,V>)U.getReferenceAcquire(tab, ((long)i << ASHIFT) + ABASE);
    }

    // 比较并交换table中索引i处的节点，如果当前值为c则更新为v
    static final <K,V> boolean casTabAt(Node<K,V>[] tab, int i,
                                        Node<K,V> c, Node<K,V> v) {
        return U.compareAndSetReference(tab, ((long)i << ASHIFT) + ABASE, c, v);
    }

    // 设置table中索引i处的节点为v，使用volatile写保证可见性
    static final <K,V> void setTabAt(Node<K,V>[] tab, int i, Node<K,V> v) {
        U.putReferenceRelease(tab, ((long)i << ASHIFT) + ABASE, v);
    }

    /* ---------------- Fields -------------- */

    /**
     * 存储元素的桶数组。在第一次插入时延迟初始化。
     * 大小总是2的幂次方。迭代器直接访问。
     */
    transient volatile Node<K,V>[] table;

    /**
     * 扩容时使用的下一个表；只有在扩容时才非空。
     */
    private transient volatile Node<K,V>[] nextTable;

    /**
     * 基础计数器值，主要用于没有竞争时，也作为表初始化竞争时的回退。
     * 通过CAS更新。类似longAdder相比于Atomic类提高了并发能力（longAdder分片他维护了一个Cell[]进行批量计数）
     */
    private transient volatile long baseCount;

    /**
     * 表初始化和扩容控制标志。
     *
     * 这是一个非常重要的控制字段，在不同阶段有不同的含义：
     *
     * 1. 当 table 为 null 时（数组未初始化）：
     *    - sizeCtl = 0          ：使用默认容量（16）进行初始化
     *    - sizeCtl > 0          ：使用指定的容量进行初始化（通过构造函数传入）
     *    （注意：此时 sizeCtl 保存的是初始容量，而不是阈值）
     *
     * 2. 当 table 正在初始化时：
     *    - sizeCtl = -1         ：表示有线程正在进行初始化操作
     *
     * 3. 当 table 已初始化（正常状态）：
     *    - sizeCtl > 0          ：表示下一次需要扩容的阈值（threshold）
     *                           计算公式：sizeCtl = 数组长度 * 负载因子（默认0.75）
     *
     * 4. 当 table 正在扩容时：
     *    - sizeCtl < -1         ：高16位存储扩容戳（resizeStamp）
     *                             低16位存储：1 + 正在扩容的线程数
     *                           例如：-2145715774 这样的负数
     *                           具体公式：sizeCtl = (resizeStamp << RESIZE_STAMP_SHIFT) + 2
     *                                   扩容线程数为 (sizeCtl & 0xFFFF) - 1
     *
     * 特殊说明：
     * - sizeCtl 是 volatile 修饰的，保证多线程可见性
     * - 通过 CAS 操作来修改，保证线程安全
     * ┌─────────────────────────────────────┐
     * │         new ConcurrentHashMap()      │
     * │         sizeCtl = 0                   │
     * └─────────────────────────────────────┘
     *                   │
     *                   ▼
     * ┌─────────────────────────────────────┐
     * │      第一次 put，调用 initTable()      │
     * ├─────────────────────────────────────┤
     * │  CAS 竞争：sizeCtl 0 → -1（成功者）    │
     * │  其他线程：sizeCtl = -1，让出 CPU      │
     * └─────────────────────────────────────┘
     *                   │
     *                   ▼
     * ┌─────────────────────────────────────┐
     * │     初始化完成，设置阈值               │
     * │     容量16，负载因子0.75               │
     * │     sizeCtl = 12                      │
     * └─────────────────────────────────────┘
     *                   │
     *                   ▼
     * ┌─────────────────────────────────────┐
     * │       正常使用，元素达到阈值           │
     * │       触发扩容                        │
     * ├─────────────────────────────────────┤
     * │  sizeCtl = (rs << 16) + 2            │
     * │  高16位：扩容戳                        │
     * │  低16位：1 + 扩容线程数                 │
     * └─────────────────────────────────────┘
     */
    private transient volatile int sizeCtl;

    /**
     * 扩容时要拆分的下一个表索引（加1）。
     */
    private transient volatile int transferIndex;

    /**
     * 在扩容和/或创建CounterCells时使用的自旋锁（通过CAS锁定）。
     */
    private transient volatile int cellsBusy;

    /**
     * 计数单元表。当非空时，大小为2的幂次方。
     * 用于高并发下的计数，减少竞争
     */
    private transient volatile CounterCell[] counterCells;

    // views
    private transient KeySetView<K,V> keySet;// 键集视图
    private transient ValuesView<K,V> values;// 值集视图
    private transient EntrySetView<K,V> entrySet;// 条目集视图


    /* ---------------- Public operations -------------- */

    /**
     * 创建一个新的空映射，使用默认的初始表大小(16)。
     * 无参构造函数，所有字段使用默认值
     */
    public ConcurrentHashMap() {
    }

    /**
     * 创建一个新的空映射，初始表大小能够容纳指定数量的元素而不需要动态扩容。
     *
     * @param initialCapacity 初始容量，实现会进行内部大小调整以容纳这么多元素
     * @throws IllegalArgumentException 如果初始容量为负数
     */
    public ConcurrentHashMap(int initialCapacity) {
        // 调用三参构造函数，使用默认负载因子0.75f和默认并发级别1
        this(initialCapacity, LOAD_FACTOR, 1);
    }

    /**
     * 创建一个新映射，包含与给定映射相同的映射关系。
     *
     * @param m 要复制的映射
     */
    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
        this.sizeCtl = DEFAULT_CAPACITY;// 设置初始容量为默认值16
        putAll(m);
    }

    /**
     * 创建一个新的空映射，初始表大小基于给定的元素数量({@code initialCapacity})和
     * 初始表密度({@code loadFactor})。
     *
     * @param initialCapacity 初始容量，实现会根据指定的负载因子进行内部大小调整以容纳这么多元素
     * @param loadFactor 用于确定初始表大小的负载因子（表密度）
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        // 调用三参构造函数，使用默认并发级别1
        this(initialCapacity, loadFactor, 1);
    }

    /**
     * 创建一个新的空映射，初始表大小基于给定的元素数量({@code initialCapacity})、
     * 初始表密度({@code loadFactor})和并发更新线程数({@code concurrencyLevel})。
     *
     * @param initialCapacity 初始容量，实现会根据指定的负载因子进行内部大小调整以容纳这么多元素
     * @param loadFactor 用于确定初始表大小的负载因子（表密度）
     * @param concurrencyLevel 估计的并发更新线程数，实现可以将此值作为大小调整的参考
     * @throws IllegalArgumentException 如果初始容量为负数，或负载因子、并发级别为非正数
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        // 参数校验：负载因子必须大于0，初始容量不能为负数，并发级别必须大于0
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();
        // 如果初始容量小于并发级别，则调整为至少等于并发级别
        // 确保至少有足够多的桶来减少线程间的竞争
        if (initialCapacity < concurrencyLevel)   // 至少使用与估计线程数一样多的桶
            initialCapacity = concurrencyLevel;   // 将初始容量设置为并发级别
        // 计算实际需要的容量：初始容量/负载因子，得到能容纳指定元素的最小容量
        long size = (long)(1.0 + (long)initialCapacity / loadFactor);
        // 将计算出的容量转换为2的幂次方，如果超过最大容量则使用最大容量
        int cap = (size >= (long) MAXIMUM_CAPACITY) ?
                MAXIMUM_CAPACITY : tableSizeFor((int) size);
        // sizeCtl在初始化前保存初始容量值，初始化后变为扩容阈值
        this.sizeCtl = cap;
    }

    // Original (since JDK1.2) Map methods

    /**
     * 获取当前映射中键值对的数量。
     */
    public int size() {
        long n = sumCount();
        return ((n < 0L) ? 0 :
                (n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
                        (int)n);// 返回累加后的总计数
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return sumCount() <= 0L; // ignore transient negative values
    }

    /**
     * 返回指定键映射到的值，如果此映射不包含该键的映射，则返回 null。
     *
     * 更正式地说，如果此映射包含从键 k 到值 v 的映射，使得 key.equals(k)，
     * 则此方法返回 v；否则返回 null。（最多只能有一个这样的映射。）
     *
     * @param key 指定的键
     * @return 键对应的值，如果不存在则返回 null
     * @throws NullPointerException 如果指定的键为 null
     */
    public V get(Object key) {
        Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;
        // 对key的hashCode进行扰动计算，减少哈希冲突
        int h = spread(key.hashCode());
        // 检查table是否已初始化，桶位置是否有元素
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (e = tabAt(tab, (n - 1) & h)) != null) {// (n-1)&h 定位桶位置
            // 检查第一个节点是否为目标节点
            if ((eh = e.hash) == h) {// 哈希值匹配
                // 检查key是否相等（先比较引用，再比较equals）
                if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                    return e.val;// 找到目标，返回值
            }
            // 处理特殊节点（哈希值为负数的情况）
            else if (eh < 0)// 负数表示特殊节点类型：ForwardingNode(-1扩容转发)、TreeBin(-2红黑树根)、ReservationNode(-3占位标记)
                return (p = e.find(h, key)) != null ? p.val : null;// 调用各自的find方法查找
            // 遍历链表查找目标节点
            while ((e = e.next) != null) {// 遍历链表
                if (e.hash == h &&// 哈希值匹配
                        ((ek = e.key) == key || (ek != null && key.equals(ek))))// key相等判断
                    return e.val;// 找到目标，返回值
            }
        }
        return null;// 未找到匹配的键，返回null
    }

    /**
     * 指定对象是否是此表中的键。
     *
     * @param key 可能的键
     * @return 如果且仅如果指定对象是此表中的键（由 equals 方法确定），则返回 true；否则返回 false
     * @throws NullPointerException 如果指定的键为 null
     */
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    /**
     * 如果此映射将一个或多个键映射到指定值，则返回 true。
     * 注意：此方法可能需要完全遍历映射，比 containsKey 方法慢得多。
     *
     * @param value 要测试是否存在于此映射中的值
     * @return 如果此映射将一个或多个键映射到指定值，则返回 true
     * @throws NullPointerException 如果指定的值为 null
     */
    public boolean containsValue(Object value) {
        // 空值检查（ConcurrentHashMap 不允许 null 值）
        if (value == null)
            throw new NullPointerException();
        // 获取当前 table 数组
        Node<K,V>[] t;
        if ((t = table) != null) {
            // 创建遍历器，从数组的起始位置开始遍历
            Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
            // 遍历所有节点
            for (Node<K,V> p; (p = it.advance()) != null; ) {
                V v;
                // 检查当前节点的值是否等于目标值
                if ((v = p.val) == value || (v != null && value.equals(v)))
                    return true;// 找到匹配的值
            }
        }
        return false;// 未找到匹配的值
    }

    /**
     * 将指定的键映射到此哈希表中的指定值。
     * 键和值都不能为null。
     *
     * <p>可以通过调用{@code get}方法并传入与原始键相等的键来检索该值。
     *
     * @param key   要与指定值关联的键
     * @param value 要与指定键关联的值
     * @return 与该{@code key}关联的先前值，如果该{@code key}没有映射关系，则返回{@code null}
     * @throws NullPointerException 如果指定的键或值为null
     */
    public V put(K key, V value) {
        return putVal(key, value, false);
    }

    /** Implementation for put and putIfAbsent */
    final V putVal(K key, V value, boolean onlyIfAbsent) {
        // CHM不允许key与value为空
        if (key == null || value == null) throw new NullPointerException();
        // 通过散列算法获取hash值（高位参与运算减少hash冲突）
        int hash = spread(key.hashCode());
        int binCount = 0;// 用于记录链表长度，后面判断是否需要树化
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh; K fk; V fv;
            // 情况1：table还未初始化，先初始化
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();// 初始化桶数组
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {// 如果桶数组目标位置为空
                // 通过cas将node存放到tab目标为i且当前值为null的位置上
                if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value)))
                    break;// 代表cas操作成功，直接结束
            }
            // 当前桶的节点正在迁移（扩容中），当前线程协助迁移
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);// 帮助迁移
            else if (onlyIfAbsent // 仅在不存在时才插入的标记（put传入false）
                    && fh == hash // 哈希值相等
                    && ((fk = f.key) == key || (fk != null && key.equals(fk))) // key相等
                    && (fv = f.val) != null) // 值不为null
                return fv;// 直接返回现有值，不进行插入
                // 其他情况——桶不为空且不是迁移状态，需要加锁处理
            else {// 操作链表或者红黑树
                V oldVal = null;
                synchronized (f) { // 对桶的头节点加锁，保证线程安全
                    if (tabAt(tab, i) == f) { // 双重检查：确保头节点没有被其他线程修改
                        if (fh >= 0) {// 链表结构（普通Node节点的hash值 >= 0）
                            binCount = 1;// 开始计数链表长度
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                // 找到相同的key
                                if (e.hash == hash &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;// 更新node的val
                                    break;// 结束
                                }
                                // 如果没有找到相同的key就一直遍历到链表尾部
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    // *** 尾插法关键：在链表末尾插入新节点 ***
                                    // 头插法在扩容时会导致环形链表，呈现出来的效果就是cpu居高不下
                                    pred.next = new Node<K,V>(hash, key, value);
                                    break;// 插入完成
                                }
                            }
                        }
                        else if (f instanceof TreeBin) {// 红黑树结构
                            Node<K,V> p;
                            binCount = 2;// 树化标记
                            // 向红黑树中插入节点（putTreeVal查找或添加节点）
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                    value)) != null) {// 如果找到了返回p，没找到返回null（会直接添加到树中）
                                oldVal = p.val;// 暂存旧值
                                if (!onlyIfAbsent)
                                    p.val = value;// 更新node的val
                            }
                        }
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
                // 插入完成后处理（判断是否需要树化）
                if (binCount != 0) {
                    // 如果链表长度达到树化阈值（默认8 链表元素个数 > 8），尝试转换为红黑树
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);// 尝试树化
                    if (oldVal != null)
                        return oldVal;// 返回旧值
                    break;
                }
            }
        }
        addCount(1L, binCount);// 增加元素数量 +1
        return null;
    }

    /**
     * 将指定映射中的所有映射关系复制到此映射中。
     * 这些映射关系将替换此映射中与指定映射中当前存在的任何键对应的所有映射关系。
     *
     * @param m 要存储在此映射中的映射关系
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        // 第一步：预扩容
        // 根据要插入的元素数量(m.size())，预先调整ConcurrentHashMap的容量
        // tryPresize会确保table数组足够大，避免在后续逐个put时频繁扩容
        tryPresize(m.size());
        // 第二步：逐个插入元素
        // 遍历传入的Map，将每个键值对通过putVal方法插入到当前Map中
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            // putVal(key, value, false) - false表示如果key存在则替换旧值
            putVal(e.getKey(), e.getValue(), false);
    }

    /**
     * Removes the key (and its corresponding value) from this map.
     * This method does nothing if the key is not in the map.
     *
     * @param  key the key that needs to be removed
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}
     * @throws NullPointerException if the specified key is null
     */
    public V remove(Object key) {
        return replaceNode(key, null, null);
    }

    /**
     * Implementation for the four public remove/replace methods:
     * Replaces node value with v, conditional upon match of cv if
     * non-null.  If resulting value is null, delete.
     */
    final V replaceNode(Object key, V value, Object cv) {
        int hash = spread(key.hashCode());
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            if (tab == null || (n = tab.length) == 0 ||
                    (f = tabAt(tab, i = (n - 1) & hash)) == null)
                break;
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else {
                V oldVal = null;
                boolean validated = false;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            validated = true;
                            for (Node<K,V> e = f, pred = null;;) {
                                K ek;
                                if (e.hash == hash &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    V ev = e.val;
                                    if (cv == null || cv == ev ||
                                            (ev != null && cv.equals(ev))) {
                                        oldVal = ev;
                                        if (value != null)
                                            e.val = value;
                                        else if (pred != null)
                                            pred.next = e.next;
                                        else
                                            setTabAt(tab, i, e.next);
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null)
                                    break;
                            }
                        }
                        else if (f instanceof TreeBin) {
                            validated = true;
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> r, p;
                            if ((r = t.root) != null &&
                                    (p = r.findTreeNode(hash, key, null)) != null) {
                                V pv = p.val;
                                if (cv == null || cv == pv ||
                                        (pv != null && cv.equals(pv))) {
                                    oldVal = pv;
                                    if (value != null)
                                        p.val = value;
                                    else if (t.removeTreeNode(p))
                                        setTabAt(tab, i, untreeify(t.first));
                                }
                            }
                        }
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
                if (validated) {
                    if (oldVal != null) {
                        if (value == null)
                            addCount(-1L, -1);
                        return oldVal;
                    }
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Removes all of the mappings from this map.
     */
    public void clear() {
        long delta = 0L; // negative number of deletions
        int i = 0;
        Node<K,V>[] tab = table;
        while (tab != null && i < tab.length) {
            int fh;
            Node<K,V> f = tabAt(tab, i);
            if (f == null)
                ++i;
            else if ((fh = f.hash) == MOVED) {
                tab = helpTransfer(tab, f);
                i = 0; // restart
            }
            else {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        Node<K,V> p = (fh >= 0 ? f :
                                (f instanceof TreeBin) ?
                                        ((TreeBin<K,V>)f).first : null);
                        while (p != null) {
                            --delta;
                            p = p.next;
                        }
                        setTabAt(tab, i++, null);
                    }
                }
            }
        }
        if (delta != 0L)
            addCount(delta, -1);
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa. The set supports element
     * removal, which removes the corresponding mapping from this map,
     * via the {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or
     * {@code addAll} operations.
     *
     * <p>The view's iterators and spliterators are
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
     *
     * @return the set view
     */
    public KeySetView<K,V> keySet() {
        KeySetView<K,V> ks;
        if ((ks = keySet) != null) return ks;
        return keySet = new KeySetView<K,V>(this, null);
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  The collection
     * supports element removal, which removes the corresponding
     * mapping from this map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll}, and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     *
     * <p>The view's iterators and spliterators are
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT}
     * and {@link Spliterator#NONNULL}.
     *
     * @return the collection view
     */
    public Collection<V> values() {
        ValuesView<K,V> vs;
        if ((vs = values) != null) return vs;
        return values = new ValuesView<K,V>(this);
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from the map,
     * via the {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.
     *
     * <p>The view's iterators and spliterators are
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
     *
     * @return the set view
     */
    public Set<Map.Entry<K,V>> entrySet() {
        EntrySetView<K,V> es;
        if ((es = entrySet) != null) return es;
        return entrySet = new EntrySetView<K,V>(this);
    }

    /**
     * Returns the hash code value for this {@link Map}, i.e.,
     * the sum of, for each key-value pair in the map,
     * {@code key.hashCode() ^ value.hashCode()}.
     *
     * @return the hash code value for this map
     */
    public int hashCode() {
        int h = 0;
        Node<K,V>[] t;
        if ((t = table) != null) {
            Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
            for (Node<K,V> p; (p = it.advance()) != null; )
                h += p.key.hashCode() ^ p.val.hashCode();
        }
        return h;
    }

    /**
     * Returns a string representation of this map.  The string
     * representation consists of a list of key-value mappings (in no
     * particular order) enclosed in braces ("{@code {}}").  Adjacent
     * mappings are separated by the characters {@code ", "} (comma
     * and space).  Each key-value mapping is rendered as the key
     * followed by an equals sign ("{@code =}") followed by the
     * associated value.
     *
     * @return a string representation of this map
     */
    public String toString() {
        Node<K,V>[] t;
        int f = (t = table) == null ? 0 : t.length;
        Traverser<K,V> it = new Traverser<K,V>(t, f, 0, f);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        Node<K,V> p;
        if ((p = it.advance()) != null) {
            for (;;) {
                K k = p.key;
                V v = p.val;
                sb.append(k == this ? "(this Map)" : k);
                sb.append('=');
                sb.append(v == this ? "(this Map)" : v);
                if ((p = it.advance()) == null)
                    break;
                sb.append(',').append(' ');
            }
        }
        return sb.append('}').toString();
    }

    /**
     * Compares the specified object with this map for equality.
     * Returns {@code true} if the given object is a map with the same
     * mappings as this map.  This operation may return misleading
     * results if either map is concurrently modified during execution
     * of this method.
     *
     * @param o object to be compared for equality with this map
     * @return {@code true} if the specified object is equal to this map
     */
    public boolean equals(Object o) {
        if (o != this) {
            if (!(o instanceof Map))
                return false;
            Map<?,?> m = (Map<?,?>) o;
            Node<K,V>[] t;
            int f = (t = table) == null ? 0 : t.length;
            Traverser<K,V> it = new Traverser<K,V>(t, f, 0, f);
            for (Node<K,V> p; (p = it.advance()) != null; ) {
                V val = p.val;
                Object v = m.get(p.key);
                if (v == null || (v != val && !v.equals(val)))
                    return false;
            }
            for (Map.Entry<?,?> e : m.entrySet()) {
                Object mk, mv, v;
                if ((mk = e.getKey()) == null ||
                        (mv = e.getValue()) == null ||
                        (v = get(mk)) == null ||
                        (mv != v && !mv.equals(v)))
                    return false;
            }
        }
        return true;
    }

    /**
     * Stripped-down version of helper class used in previous version,
     * declared for the sake of serialization compatibility.
     */
    static class Segment<K,V> extends ReentrantLock implements Serializable {
        private static final long serialVersionUID = 2249069246763182397L;
        final float loadFactor;
        Segment(float lf) { this.loadFactor = lf; }
    }

    /**
     * Saves this map to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData
     * the serialized fields, followed by the key (Object) and value
     * (Object) for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        // For serialization compatibility
        // Emulate segment calculation from previous version of this class
        int sshift = 0;
        int ssize = 1;
        while (ssize < DEFAULT_CONCURRENCY_LEVEL) {
            ++sshift;
            ssize <<= 1;
        }
        int segmentShift = 32 - sshift;
        int segmentMask = ssize - 1;
        @SuppressWarnings("unchecked")
        Segment<K,V>[] segments = (Segment<K,V>[])
                new Segment<?,?>[DEFAULT_CONCURRENCY_LEVEL];
        for (int i = 0; i < segments.length; ++i)
            segments[i] = new Segment<K,V>(LOAD_FACTOR);
        java.io.ObjectOutputStream.PutField streamFields = s.putFields();
        streamFields.put("segments", segments);
        streamFields.put("segmentShift", segmentShift);
        streamFields.put("segmentMask", segmentMask);
        s.writeFields();

        Node<K,V>[] t;
        if ((t = table) != null) {
            Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
            for (Node<K,V> p; (p = it.advance()) != null; ) {
                s.writeObject(p.key);
                s.writeObject(p.val);
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }

    /**
     * Reconstitutes this map from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        /*
         * To improve performance in typical cases, we create nodes
         * while reading, then place in table once size is known.
         * However, we must also validate uniqueness and deal with
         * overpopulated bins while doing so, which requires
         * specialized versions of putVal mechanics.
         */
        sizeCtl = -1; // force exclusion for table construction
        s.defaultReadObject();
        long size = 0L;
        Node<K,V> p = null;
        for (;;) {
            @SuppressWarnings("unchecked")
            K k = (K) s.readObject();
            @SuppressWarnings("unchecked")
            V v = (V) s.readObject();
            if (k != null && v != null) {
                p = new Node<K,V>(spread(k.hashCode()), k, v, p);
                ++size;
            }
            else
                break;
        }
        if (size == 0L)
            sizeCtl = 0;
        else {
            long ts = (long)(1.0 + size / LOAD_FACTOR);
            int n = (ts >= (long)MAXIMUM_CAPACITY) ?
                    MAXIMUM_CAPACITY : tableSizeFor((int)ts);
            @SuppressWarnings("unchecked")
            Node<K,V>[] tab = (Node<K,V>[])new Node<?,?>[n];
            int mask = n - 1;
            long added = 0L;
            while (p != null) {
                boolean insertAtFront;
                Node<K,V> next = p.next, first;
                int h = p.hash, j = h & mask;
                if ((first = tabAt(tab, j)) == null)
                    insertAtFront = true;
                else {
                    K k = p.key;
                    if (first.hash < 0) {
                        TreeBin<K,V> t = (TreeBin<K,V>)first;
                        if (t.putTreeVal(h, k, p.val) == null)
                            ++added;
                        insertAtFront = false;
                    }
                    else {
                        int binCount = 0;
                        insertAtFront = true;
                        Node<K,V> q; K qk;
                        for (q = first; q != null; q = q.next) {
                            if (q.hash == h &&
                                    ((qk = q.key) == k ||
                                            (qk != null && k.equals(qk)))) {
                                insertAtFront = false;
                                break;
                            }
                            ++binCount;
                        }
                        if (insertAtFront && binCount >= TREEIFY_THRESHOLD) {
                            insertAtFront = false;
                            ++added;
                            p.next = first;
                            TreeNode<K,V> hd = null, tl = null;
                            for (q = p; q != null; q = q.next) {
                                TreeNode<K,V> t = new TreeNode<K,V>
                                        (q.hash, q.key, q.val, null, null);
                                if ((t.prev = tl) == null)
                                    hd = t;
                                else
                                    tl.next = t;
                                tl = t;
                            }
                            setTabAt(tab, j, new TreeBin<K,V>(hd));
                        }
                    }
                }
                if (insertAtFront) {
                    ++added;
                    p.next = first;
                    setTabAt(tab, j, p);
                }
                p = next;
            }
            table = tab;
            sizeCtl = n - (n >>> 2);
            baseCount = added;
        }
    }

    // ConcurrentMap methods

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or {@code null} if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    public V putIfAbsent(K key, V value) {
        return putVal(key, value, true);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    public boolean remove(Object key, Object value) {
        if (key == null)
            throw new NullPointerException();
        return value != null && replaceNode(key, null, value) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if any of the arguments are null
     */
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null)
            throw new NullPointerException();
        return replaceNode(key, newValue, oldValue) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or {@code null} if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    public V replace(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();
        return replaceNode(key, value, null);
    }

    // Overrides of JDK8+ Map extension method defaults

    /**
     * Returns the value to which the specified key is mapped, or the
     * given default value if this map contains no mapping for the
     * key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the value to return if this map contains
     * no mapping for the given key
     * @return the mapping for the key, if present; else the default value
     * @throws NullPointerException if the specified key is null
     */
    public V getOrDefault(Object key, V defaultValue) {
        V v;
        return (v = get(key)) == null ? defaultValue : v;
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) throw new NullPointerException();
        Node<K,V>[] t;
        if ((t = table) != null) {
            Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
            for (Node<K,V> p; (p = it.advance()) != null; ) {
                action.accept(p.key, p.val);
            }
        }
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) throw new NullPointerException();
        Node<K,V>[] t;
        if ((t = table) != null) {
            Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
            for (Node<K,V> p; (p = it.advance()) != null; ) {
                V oldValue = p.val;
                for (K key = p.key;;) {
                    V newValue = function.apply(key, oldValue);
                    if (newValue == null)
                        throw new NullPointerException();
                    if (replaceNode(key, newValue, oldValue) != null ||
                            (oldValue = get(key)) == null)
                        break;
                }
            }
        }
    }

    /**
     * Helper method for EntrySetView.removeIf.
     */
    boolean removeEntryIf(Predicate<? super Entry<K,V>> function) {
        if (function == null) throw new NullPointerException();
        Node<K,V>[] t;
        boolean removed = false;
        if ((t = table) != null) {
            Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
            for (Node<K,V> p; (p = it.advance()) != null; ) {
                K k = p.key;
                V v = p.val;
                Map.Entry<K,V> e = new AbstractMap.SimpleImmutableEntry<>(k, v);
                if (function.test(e) && replaceNode(k, null, v) != null)
                    removed = true;
            }
        }
        return removed;
    }

    /**
     * Helper method for ValuesView.removeIf.
     */
    boolean removeValueIf(Predicate<? super V> function) {
        if (function == null) throw new NullPointerException();
        Node<K,V>[] t;
        boolean removed = false;
        if ((t = table) != null) {
            Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
            for (Node<K,V> p; (p = it.advance()) != null; ) {
                K k = p.key;
                V v = p.val;
                if (function.test(v) && replaceNode(k, null, v) != null)
                    removed = true;
            }
        }
        return removed;
    }

    /**
     * If the specified key is not already associated with a value,
     * attempts to compute its value using the given mapping function
     * and enters it into this map unless {@code null}.  The entire
     * method invocation is performed atomically.  The supplied
     * function is invoked exactly once per invocation of this method
     * if the key is absent, else not at all.  Some attempted update
     * operations on this map by other threads may be blocked while
     * computation is in progress, so the computation should be short
     * and simple.
     *
     * <p>The mapping function must not modify this map during computation.
     *
     * @param key key with which the specified value is to be associated
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with
     *         the specified key, or null if the computed value is null
     * @throws NullPointerException if the specified key or mappingFunction
     *         is null
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete
     * @throws RuntimeException or Error if the mappingFunction does so,
     *         in which case the mapping is left unestablished
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null)
            throw new NullPointerException();
        int h = spread(key.hashCode());
        V val = null;
        int binCount = 0;
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh; K fk; V fv;
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
                Node<K,V> r = new ReservationNode<K,V>();
                synchronized (r) {
                    if (casTabAt(tab, i, null, r)) {
                        binCount = 1;
                        Node<K,V> node = null;
                        try {
                            if ((val = mappingFunction.apply(key)) != null)
                                node = new Node<K,V>(h, key, val);
                        } finally {
                            setTabAt(tab, i, node);
                        }
                    }
                }
                if (binCount != 0)
                    break;
            }
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else if (fh == h    // check first node without acquiring lock
                    && ((fk = f.key) == key || (fk != null && key.equals(fk)))
                    && (fv = f.val) != null)
                return fv;
            else {
                boolean added = false;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                if (e.hash == h &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    val = e.val;
                                    break;
                                }
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    if ((val = mappingFunction.apply(key)) != null) {
                                        if (pred.next != null)
                                            throw new IllegalStateException("Recursive update");
                                        added = true;
                                        pred.next = new Node<K,V>(h, key, val);
                                    }
                                    break;
                                }
                            }
                        }
                        else if (f instanceof TreeBin) {
                            binCount = 2;
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> r, p;
                            if ((r = t.root) != null &&
                                    (p = r.findTreeNode(h, key, null)) != null)
                                val = p.val;
                            else if ((val = mappingFunction.apply(key)) != null) {
                                added = true;
                                t.putTreeVal(h, key, val);
                            }
                        }
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (!added)
                        return val;
                    break;
                }
            }
        }
        if (val != null)
            addCount(1L, binCount);
        return val;
    }

    /**
     * If the value for the specified key is present, attempts to
     * compute a new mapping given the key and its current mapped
     * value.  The entire method invocation is performed atomically.
     * The supplied function is invoked exactly once per invocation of
     * this method if the key is present, else not at all.  Some
     * attempted update operations on this map by other threads may be
     * blocked while computation is in progress, so the computation
     * should be short and simple.
     *
     * <p>The remapping function must not modify this map during computation.
     *
     * @param key key with which a value may be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key or remappingFunction
     *         is null
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete
     * @throws RuntimeException or Error if the remappingFunction does so,
     *         in which case the mapping is unchanged
     */
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null)
            throw new NullPointerException();
        int h = spread(key.hashCode());
        V val = null;
        int delta = 0;
        int binCount = 0;
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & h)) == null)
                break;
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f, pred = null;; ++binCount) {
                                K ek;
                                if (e.hash == h &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    val = remappingFunction.apply(key, e.val);
                                    if (val != null)
                                        e.val = val;
                                    else {
                                        delta = -1;
                                        Node<K,V> en = e.next;
                                        if (pred != null)
                                            pred.next = en;
                                        else
                                            setTabAt(tab, i, en);
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null)
                                    break;
                            }
                        }
                        else if (f instanceof TreeBin) {
                            binCount = 2;
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> r, p;
                            if ((r = t.root) != null &&
                                    (p = r.findTreeNode(h, key, null)) != null) {
                                val = remappingFunction.apply(key, p.val);
                                if (val != null)
                                    p.val = val;
                                else {
                                    delta = -1;
                                    if (t.removeTreeNode(p))
                                        setTabAt(tab, i, untreeify(t.first));
                                }
                            }
                        }
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
                if (binCount != 0)
                    break;
            }
        }
        if (delta != 0)
            addCount((long)delta, binCount);
        return val;
    }

    /**
     * Attempts to compute a mapping for the specified key and its
     * current mapped value (or {@code null} if there is no current
     * mapping). The entire method invocation is performed atomically.
     * The supplied function is invoked exactly once per invocation of
     * this method.  Some attempted update operations on this map by
     * other threads may be blocked while computation is in progress,
     * so the computation should be short and simple.
     *
     * <p>The remapping function must not modify this map during computation.
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key or remappingFunction
     *         is null
     * @throws IllegalStateException if the computation detectably
     *         attempts a recursive update to this map that would
     *         otherwise never complete
     * @throws RuntimeException or Error if the remappingFunction does so,
     *         in which case the mapping is unchanged
     */
    public V compute(K key,
                     BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null)
            throw new NullPointerException();
        int h = spread(key.hashCode());
        V val = null;
        int delta = 0;
        int binCount = 0;
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
                Node<K,V> r = new ReservationNode<K,V>();
                synchronized (r) {
                    if (casTabAt(tab, i, null, r)) {
                        binCount = 1;
                        Node<K,V> node = null;
                        try {
                            if ((val = remappingFunction.apply(key, null)) != null) {
                                delta = 1;
                                node = new Node<K,V>(h, key, val);
                            }
                        } finally {
                            setTabAt(tab, i, node);
                        }
                    }
                }
                if (binCount != 0)
                    break;
            }
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f, pred = null;; ++binCount) {
                                K ek;
                                if (e.hash == h &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    val = remappingFunction.apply(key, e.val);
                                    if (val != null)
                                        e.val = val;
                                    else {
                                        delta = -1;
                                        Node<K,V> en = e.next;
                                        if (pred != null)
                                            pred.next = en;
                                        else
                                            setTabAt(tab, i, en);
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null) {
                                    val = remappingFunction.apply(key, null);
                                    if (val != null) {
                                        if (pred.next != null)
                                            throw new IllegalStateException("Recursive update");
                                        delta = 1;
                                        pred.next = new Node<K,V>(h, key, val);
                                    }
                                    break;
                                }
                            }
                        }
                        else if (f instanceof TreeBin) {
                            binCount = 1;
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> r, p;
                            if ((r = t.root) != null)
                                p = r.findTreeNode(h, key, null);
                            else
                                p = null;
                            V pv = (p == null) ? null : p.val;
                            val = remappingFunction.apply(key, pv);
                            if (val != null) {
                                if (p != null)
                                    p.val = val;
                                else {
                                    delta = 1;
                                    t.putTreeVal(h, key, val);
                                }
                            }
                            else if (p != null) {
                                delta = -1;
                                if (t.removeTreeNode(p))
                                    setTabAt(tab, i, untreeify(t.first));
                            }
                        }
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    break;
                }
            }
        }
        if (delta != 0)
            addCount((long)delta, binCount);
        return val;
    }

    /**
     * If the specified key is not already associated with a
     * (non-null) value, associates it with the given value.
     * Otherwise, replaces the value with the results of the given
     * remapping function, or removes if {@code null}. The entire
     * method invocation is performed atomically.  Some attempted
     * update operations on this map by other threads may be blocked
     * while computation is in progress, so the computation should be
     * short and simple, and must not attempt to update any other
     * mappings of this Map.
     *
     * @param key key with which the specified value is to be associated
     * @param value the value to use if absent
     * @param remappingFunction the function to recompute a value if present
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key or the
     *         remappingFunction is null
     * @throws RuntimeException or Error if the remappingFunction does so,
     *         in which case the mapping is unchanged
     */
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null)
            throw new NullPointerException();
        int h = spread(key.hashCode());
        V val = null;
        int delta = 0;
        int binCount = 0;
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
                if (casTabAt(tab, i, null, new Node<K,V>(h, key, value))) {
                    delta = 1;
                    val = value;
                    break;
                }
            }
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else {
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f, pred = null;; ++binCount) {
                                K ek;
                                if (e.hash == h &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    val = remappingFunction.apply(e.val, value);
                                    if (val != null)
                                        e.val = val;
                                    else {
                                        delta = -1;
                                        Node<K,V> en = e.next;
                                        if (pred != null)
                                            pred.next = en;
                                        else
                                            setTabAt(tab, i, en);
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null) {
                                    delta = 1;
                                    val = value;
                                    pred.next = new Node<K,V>(h, key, val);
                                    break;
                                }
                            }
                        }
                        else if (f instanceof TreeBin) {
                            binCount = 2;
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> r = t.root;
                            TreeNode<K,V> p = (r == null) ? null :
                                    r.findTreeNode(h, key, null);
                            val = (p == null) ? value :
                                    remappingFunction.apply(p.val, value);
                            if (val != null) {
                                if (p != null)
                                    p.val = val;
                                else {
                                    delta = 1;
                                    t.putTreeVal(h, key, val);
                                }
                            }
                            else if (p != null) {
                                delta = -1;
                                if (t.removeTreeNode(p))
                                    setTabAt(tab, i, untreeify(t.first));
                            }
                        }
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    break;
                }
            }
        }
        if (delta != 0)
            addCount((long)delta, binCount);
        return val;
    }

    // Hashtable legacy methods

    /**
     * Tests if some key maps into the specified value in this table.
     *
     * <p>Note that this method is identical in functionality to
     * {@link #containsValue(Object)}, and exists solely to ensure
     * full compatibility with class {@link java.util.Hashtable},
     * which supported this method prior to introduction of the
     * Java Collections Framework.
     *
     * @param  value a value to search for
     * @return {@code true} if and only if some key maps to the
     *         {@code value} argument in this table as
     *         determined by the {@code equals} method;
     *         {@code false} otherwise
     * @throws NullPointerException if the specified value is null
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        Node<K,V>[] t;
        int f = (t = table) == null ? 0 : t.length;
        return new KeyIterator<K,V>(t, f, 0, f, this);
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        Node<K,V>[] t;
        int f = (t = table) == null ? 0 : t.length;
        return new ValueIterator<K,V>(t, f, 0, f, this);
    }

    // ConcurrentHashMap-only methods

    /**
     * Returns the number of mappings. This method should be used
     * instead of {@link #size} because a ConcurrentHashMap may
     * contain more mappings than can be represented as an int. The
     * value returned is an estimate; the actual count may differ if
     * there are concurrent insertions or removals.
     *
     * @return the number of mappings
     * @since 1.8
     */
    public long mappingCount() {
        long n = sumCount();
        return (n < 0L) ? 0L : n; // ignore transient negative values
    }

    /**
     * Creates a new {@link Set} backed by a ConcurrentHashMap
     * from the given type to {@code Boolean.TRUE}.
     *
     * @param <K> the element type of the returned set
     * @return the new set
     * @since 1.8
     */
    public static <K> KeySetView<K,Boolean> newKeySet() {
        return new KeySetView<K,Boolean>
                (new ConcurrentHashMap<K,Boolean>(), Boolean.TRUE);
    }

    /**
     * Creates a new {@link Set} backed by a ConcurrentHashMap
     * from the given type to {@code Boolean.TRUE}.
     *
     * @param initialCapacity The implementation performs internal
     * sizing to accommodate this many elements.
     * @param <K> the element type of the returned set
     * @return the new set
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative
     * @since 1.8
     */
    public static <K> KeySetView<K,Boolean> newKeySet(int initialCapacity) {
        return new KeySetView<K,Boolean>
                (new ConcurrentHashMap<K,Boolean>(initialCapacity), Boolean.TRUE);
    }

    /**
     * Returns a {@link Set} view of the keys in this map, using the
     * given common mapped value for any additions (i.e., {@link
     * Collection#add} and {@link Collection#addAll(Collection)}).
     * This is of course only appropriate if it is acceptable to use
     * the same value for all additions from this view.
     *
     * @param mappedValue the mapped value to use for any additions
     * @return the set view
     * @throws NullPointerException if the mappedValue is null
     */
    public KeySetView<K,V> keySet(V mappedValue) {
        if (mappedValue == null)
            throw new NullPointerException();
        return new KeySetView<K,V>(this, mappedValue);
    }

    /* ---------------- Special Nodes -------------- */

    /**
     * A node inserted at head of bins during transfer operations.
     */
    static final class ForwardingNode<K,V> extends Node<K,V> {
        final Node<K,V>[] nextTable;
        ForwardingNode(Node<K,V>[] tab) {
            super(MOVED, null, null);
            this.nextTable = tab;
        }

        Node<K,V> find(int h, Object k) {
            // loop to avoid arbitrarily deep recursion on forwarding nodes
            outer: for (Node<K,V>[] tab = nextTable;;) {
                Node<K,V> e; int n;
                if (k == null || tab == null || (n = tab.length) == 0 ||
                        (e = tabAt(tab, (n - 1) & h)) == null)
                    return null;
                for (;;) {
                    int eh; K ek;
                    if ((eh = e.hash) == h &&
                            ((ek = e.key) == k || (ek != null && k.equals(ek))))
                        return e;
                    if (eh < 0) {
                        if (e instanceof ForwardingNode) {
                            tab = ((ForwardingNode<K,V>)e).nextTable;
                            continue outer;
                        }
                        else
                            return e.find(h, k);
                    }
                    if ((e = e.next) == null)
                        return null;
                }
            }
        }
    }

    /**
     * A place-holder node used in computeIfAbsent and compute.
     */
    static final class ReservationNode<K,V> extends Node<K,V> {
        ReservationNode() {
            super(RESERVED, null, null);
        }

        Node<K,V> find(int h, Object k) {
            return null;
        }
    }

    /* ---------------- Table Initialization and Resizing -------------- */

    /**
     * Returns the stamp bits for resizing a table of size n.
     * Must be negative when shifted left by RESIZE_STAMP_SHIFT.
     */
    static final int resizeStamp(int n) {
        //可以把扩容戳理解为一次扩容活动的版本号或会议 ID。
        //
        //唯一性：它由旧数组长度唯一确定，保证了所有参与同一批扩容的线程拿到的 ID 都一样。
        //
        //状态标记：它被封装在 sizeCtl 变量中，使得 sizeCtl 不仅能告诉其他线程“现在正在扩容”，还能通过高16位告诉他们“正在扩哪个版本的表”，
        //
        // 通过低16位统计“有多少人正在干活”。
        //
        //通过这种设计，ConcurrentHashMap 实现了让多个线程并发地将旧数组的数据迁移到新数组，而不会相互干扰或混淆版本
        return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
    }

    /**
     * Initializes table, using the size recorded in sizeCtl.
     */
    private final Node<K,V>[] initTable() {
        Node<K,V>[] tab; int sc;// 定义局部变量：tab用于存储table引用，sc用于存储sizeCtl的值
        while ((tab = table) == null || tab.length == 0) {// 自旋：只要table还未初始化，就不断尝试
            if ((sc = sizeCtl) < 0)// 如果sizeCtl小于0，说明已经有线程在进行初始化或扩容
                Thread.yield(); // 当前线程让出CPU时间片，等待其他线程完成初始化（自旋等待）
            else if (U.compareAndSetInt(this, SIZECTL, sc, -1)) {// 尝试通过CAS将SIZECTL从sc设置为-1，-1表示当前线程正在初始化
                try {
                    if ((tab = table) == null || tab.length == 0) {// 双重检查：再次检查table是否已被其他线程初始化
                        // 确定初始化容量：如果sc大于0则用sc（代表数组指定了具体的初始化长度），否则用默认容量DEFAULT_CAPACITY(16)
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;

                        // 正常new数组并且指定大小
                        @SuppressWarnings("unchecked")
                        Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                        // 初始化全局变量table
                        table = tab = nt;
                        // 计算下次扩容的阈值
                        sc = n - (n >>> 2);// sc = n * 0.75
                    }
                } finally {
                    sizeCtl = sc;// 将计算好的扩容阈值赋给sizeCtl
                }
                break; // 初始化完成，跳出循环
            }
        }
        return tab;
    }

    /**
     * 模仿的LongAdder的思路
     * 增加计数，如果表太小且尚未扩容，则启动传输。
     * 如果已经在扩容，且有可用的工作，则帮助执行传输。
     * 传输后重新检查占用情况，看是否需要另一次扩容（因为扩容滞后于添加）。
     *
     * @param x 要添加的计数（可以是负数，如remove操作）
     * @param check 如果<0，不检查扩容；如果<=1，仅在无竞争时检查
     */
    private final void addCount(long x, int check) {
        CounterCell[] cs; long b, s;// 获取当前的计数器单元格数组和基础计数值
        if ((cs = counterCells) != null ||// 如果计数器单元格数组不为空，说明有线程正在使用计数器单元格
                !U.compareAndSetLong(this, BASECOUNT, b = baseCount, s = b + x)) {// 通过cas尝试更新baseCount = baseCount + x
            // 情况1: counterCells已存在 或 情况2: CAS更新baseCount失败（有竞争）
            CounterCell c; long v; int m; // 声明一个计数器单元格引用、声明一个临时变量用于存储单元格的旧值、声明一个临时变量用于存储数组长度减1
            boolean uncontended = true;// 标记当前线程的CAS操作是否无竞争，初始化为true

            // 判断条件1：cs == null —— 数组为null（说明CAS失败但数组还未初始化）
            // 判断条件2：(m = cs.length - 1) < 0 —— 数组长度为0（防御性判断，理论上不会发生）
            // 判断条件3：(c = cs[ThreadLocalRandom.getProbe() & m]) == null —— 当前线程对应的数组位置还没有创建CounterCell对象
            // 判断条件4：!(uncontended = U.compareAndSetLong(c, CELLVALUE, v = c.value, v + x))
            //          —— CAS尝试更新当前线程对应的CounterCell的值失败（说明有其他线程也在操作同一个单元格）
            // 如果任意条件为真，说明无法直接在当前单元格上进行计数，需要调用fullAddCount进行完整的分支处理
            if (cs == null || (m = cs.length - 1) < 0 ||
                    (c = cs[ThreadLocalRandom.getProbe() & m]) == null ||
                    !(uncontended =
                            U.compareAndSetLong(c, CELLVALUE, v = c.value, v + x))) {
                // 调用fullAddCount方法处理复杂的计数增加逻辑（初始化数组、创建单元格、扩容单元格数组等）
                fullAddCount(x, uncontended);
                return;
            }

            // 如果代码能执行到这里，说明计数已经成功在对应的CounterCell上通过CAS完成
            // 如果check <= 1，表示不需要进行扩容检查（check <= 1的场景通常是putIfAbsent等操作）
            if (check <= 1)
                return;// 直接返回，不进行后续的扩容检查
            // 如果需要检查扩容，则计算当前的总元素个数（遍历所有计数器单元格并累加baseCount）
            s = sumCount();
        }

        // 执行到这里，说明计数已经完成，并且需要检查是否需要扩容（check >= 0）
        if (check >= 0) {
            // 声明局部变量：当前哈希表数组、新哈希表数组、数组长度、sizeCtl的临时值
            Node<K,V>[] tab, nt; int n, sc;
            // 循环条件：当前元素总数s大于等于扩容阈值(sizeCtl)
            // 并且 当前哈希表不为null 并且 当前哈希表长度小于最大容量
            while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
                    (n = tab.length) < MAXIMUM_CAPACITY) {
                // 计算扩容戳，左移RESIZE_STAMP_SHIFT位，得到用于sizeCtl的标记值
                // resizeStamp(n)会根据数组长度n生成一个唯一的标识戳
                int rs = resizeStamp(n) << RESIZE_STAMP_SHIFT;
                // 判断sizeCtl是否小于0，小于0表示当前正在进行扩容
                if (sc < 0) {
                    // sc < 0 表示正在扩容，判断是否需要协助扩容

                    // 条件1：sc == rs + MAX_RESIZERS —— 已经达到最大扩容线程数，无法再协助
                    // 条件2：sc == rs + 1 —— 扩容已经完成（这是一个特殊的判断，实际含义是最后一个线程退出扩容）
                    // 条件3：(nt = nextTable) == null —— 新表为null，说明扩容已经结束
                    // 条件4：transferIndex <= 0 —— 已经没有可分配的迁移任务
                    // 如果满足上述任一条件，则无法协助扩容，跳出循环
                    if (sc == rs + MAX_RESIZERS || sc == rs + 1 ||
                            (nt = nextTable) == null || transferIndex <= 0)
                        break;
                    // 否则，尝试将sizeCtl增加1（表示加入一个扩容线程）
                    if (U.compareAndSetInt(this, SIZECTL, sc, sc + 1))
                        // CAS成功，调用transfer方法协助扩容，传入当前哈希表和已存在的新哈希表
                        transfer(tab, nt);
                }
                // sc >= 0，表示还没有开始扩容，当前线程尝试成为第一个发起扩容的线程
                else if (U.compareAndSetInt(this, SIZECTL, sc, rs + 2))
                    // CAS将sizeCtl设置为(rs + 2)，表示有1个线程在进行扩容
                    // 调用transfer方法开始扩容，第二个参数为null表示需要创建新的哈希表
                    transfer(tab, null);
                // 扩容后，重新计算元素总数（因为扩容过程中可能有其他线程继续添加元素）
                s = sumCount();
            }
        }
    }

    /**
     * Helps transfer if a resize is in progress.
     */
    final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
        Node<K,V>[] nextTab; int sc;
        if (tab != null && (f instanceof ForwardingNode) &&
                (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {
            int rs = resizeStamp(tab.length) << RESIZE_STAMP_SHIFT;
            while (nextTab == nextTable && table == tab &&
                    (sc = sizeCtl) < 0) {
                if (sc == rs + MAX_RESIZERS || sc == rs + 1 ||
                        transferIndex <= 0)
                    break;
                if (U.compareAndSetInt(this, SIZECTL, sc, sc + 1)) {
                    transfer(tab, nextTab);
                    break;
                }
            }
            return nextTab;
        }
        return table;
    }

    /**
     * 尝试预扩容table以容纳指定数量的元素。
     *
     * @param size 元素数量
     */
    private final void tryPresize(int size) {
        // 第一步：计算目标容量c
        // 如果size已经超过最大容量的一半，直接设为最大容量
        // 否则计算：size + size/2 + 1，然后取大于等于这个数的最小2的幂
        // 例如：size=100 → 100+50+1=151 → tableSizeFor(151)=256
        int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY :
                tableSizeFor(size + (size >>> 1) + 1);
        int sc;
        // sizeCtl >= 0 表示没有线程在进行扩容或初始化 (-1说明有线程在初始化，小于-1说明有线程在扩容)
        // 如果<0，说明其他线程已经在扩容了，当前线程不需要重复操作
        while ((sc = sizeCtl) >= 0) {// 开始初始化
            Node<K,V>[] tab = table; int n;
            // 场景1：table为null或长度为0，说明整个Map还没初始化
            if (tab == null || (n = tab.length) == 0) {
                // 决定初始容量：取构造函数传入的容量(sc)和计算出的容量(c)中较大的
                // 例如：new ConcurrentHashMap(20)时，sc=20，c计算出来可能是256，取256
                n = (sc > c) ? sc : c;
                // 通过CAS将sizeCtl从sc设置为-1，表示当前线程正在初始化
                // 如果CAS失败，说明有其他线程竞争初始化，会重试循环
                if (U.compareAndSetInt(this, SIZECTL, sc, -1)) {// 确认可以开始初始化
                    try {
                        if (table == tab) {// 再次检查table是否还是原来的值（双重检查锁定）
                            @SuppressWarnings("unchecked")
                            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                            table = nt;// 赋值给table
                            // 计算新的阈值：n - n/4 = n * 3/4 = 0.75 * n
                            sc = n - (n >>> 2);
                        }
                    } finally {
                        // 将sizeCtl设置为计算出的阈值
                        // 其他线程看到sizeCtl>0，就知道初始化已完成
                        sizeCtl = sc;
                    }
                }
            }
            // 场景2：已经初始化过了，检查是否需要扩容
            // 条件1：c <= sc —— 需要的容量小于等于当前阈值，不用扩容
            // 条件2：n >= MAXIMUM_CAPACITY —— 已经达到最大容量，无法扩容
            else if (c <= sc || n >= MAXIMUM_CAPACITY)
                break;
                // 场景3：需要扩容，且table没有被其他线程改变（已经初始化完成）
            else if (tab == table) {
                // resizeStamp(n)会生成一个唯一标识这次扩容的标记--得到一个长度为32位的数值，高十六位做扩容标识，低十六位做扩容线程数（等于2说明有一个线程在做扩容）
                // 根据老数组长度n生成扩容戳（因为ConcurrentHashMap允许多线程迁移数据）
                // A线程 16 ~ 32 √ 可以协助扩容
                // B线程 16 ~ 32 √ 可以协助扩容
                // C线程 32 ~ 64 ×
                int rs = resizeStamp(n);// 只有扩容标识戳一致的时候才能一起扩容
                // CAS尝试将sizeCtl从sc设置为扩容标记
                if (U.compareAndSetInt(this, SIZECTL, sc,
                        (rs << RESIZE_STAMP_SHIFT) + 2))// 设置为2代表有一个线程在做扩容此时sizeCtl = -2
                    // 调用transfer开始真正的扩容
                    // 第二个参数为null表示需要创建新的nextTable
                    transfer(tab, null);
            }
        }
    }

    /**
     * 将每个桶中的节点移动/复制到新的哈希表中。
     * 这是并发扩容的核心实现。
     *
     * 第一个线程（线程A）开始扩容
     * 线程A发现需要扩容，它创建新数组（长度为64）。
     * 它从 transferIndex 变量中领取任务。
     * transferIndex 是一个共享的原子变量，初始值是 n（32）。
     * 线程A通过 CAS 操作将 transferIndex 从 32 减去步长 16，变成 16，成功领取到区间 (16, 31]。
     * 它开始处理索引 31、30 ... 16，将这些桶里的链表或红黑树迁移到新数组（长度为64）的对应位置（i 和 i + n）。
     * 每迁移完一个桶，就在旧数组的那个桶位置放一个 ForwardingNode。
     *
     * 第二个线程（线程B）前来帮助
     * 线程B想要 put 一个元素，发现命中的桶上是一个 ForwardingNode（也就是你提到的 (fh = f.hash) == MOVED 这种情况）。
     * 线程B不会干等着，而是调用 helpTransfer() 方法加入扩容。
     * 它同样会去查看全局的 transferIndex 变量，此时 transferIndex 的值是 16。
     * 线程B通过 CAS 操作将 transferIndex 从 16 减去步长 16，变成 0，成功领取到区间 (0, 15]。
     * 它开始处理索引 15、14 ... 0，迁移这些低位桶。
     * 每迁移完一个桶，同样放置 ForwardingNode
     *
     *
     * @param tab 旧的哈希表数组
     * @param nextTab 新的哈希表数组（如果为null，则需要创建）
     */
    private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
        // 获取旧表的长度
        int n = tab.length, stride;// 步长

        // 第一步：计算每个线程处理的步长（桶的数量）
        // 多核CPU：n/8/CPU核心数，单核：n
        // 目的是让每个线程处理大致相等数量的桶，均衡负载
        if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
            // 如果计算出的步长小于最小步长，则使用最小步长16
            // 最小步长16，避免线程过多导致任务过碎
            stride = MIN_TRANSFER_STRIDE;

        // 第二步：初始化新表（如果是第一个发起扩容的线程）
        if (nextTab == null) {            // 第一个线程进入，需要创建新表
            try {
                @SuppressWarnings("unchecked")
                // 创建新表，容量为旧表的2倍（左移1位相当于乘以2）
                Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
                nextTab = nt;
            } catch (Throwable ex) {      // 处理内存溢出异常
                // 如果内存不足，将sizeCtl设置为Integer最大值，表示不再扩容
                sizeCtl = Integer.MAX_VALUE;
                return;
            }
            // 将新表赋值给nextTable（全局变量，用于其他线程获取）
            nextTable = nextTab;
            // 设置transferIndex为旧数组长度，从最后一个桶开始分配任务
            transferIndex = n;
        }

        // 获取新表的长度
        int nextn = nextTab.length;
        // 创建一个转发节点，用于标记已经迁移完成的桶
        ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
        // advance：是否继续获取下一个桶的标志
        boolean advance = true;
        // finishing：是否所有桶都迁移完成的标志
        boolean finishing = false; // 是否所有桶都迁移完成

        // 第三步：主循环，处理每个桶的迁移
        for (int i = 0, bound = 0;;) {
            Node<K,V> f; int fh;

            // 第四步：获取下一个要处理的桶的索引（领取任务的while循环）
            while (advance) {
                int nextIndex, nextBound;

                // 如果当前索引i大于等于bound，或者正在完成阶段，则停止获取下一个桶（第一个判断是为了签一下一个索引数据）
                if (--i >= bound || finishing)
                    advance = false;
                    // 如果没有更多可分配的桶了，设置i为-1（说明没有任务可以领取了）
                else if ((nextIndex = transferIndex) <= 0) {
                    i = -1;
                    advance = false;
                }
                // 尝试分配新的桶范围（通过CAS更新transferIndex）
                else if (U.compareAndSetInt
                        (this, TRANSFERINDEX, nextIndex,
                                nextBound = (nextIndex > stride ?// 如果更新成功了，假如旧数组长度为32，那么transferIndex此时为32-步长等于16
                                        nextIndex - stride : 0))) {
                    /**
                     * 假设旧数组长度为32
                     *
                     * 此时更新transferIndex成功
                     * transferIndex = 16
                     * nextIndex = 32
                     * nextBound = 16
                     * bound = 16
                     * i = 32 - 1 = 31
                     *
                     * 以上步骤如此反复领取任务
                     */
                    // 分配成功，设置bound为新的下界，i为新的上界 === 31 ~ 16
                    bound = nextBound;
                    i = nextIndex - 1;
                    advance = false;
                }
            }

            // 第五步：检查是否所有桶都处理完成
            if (i < 0 || i >= n || i + n >= nextn) {
                int sc;
                // 如果完成阶段标志为true，则完成整个迁移过程
                if (finishing) {
                    nextTable = null;           // 清空nextTable
                    table = nextTab;             // 将table指向新表
                    // 设置新容量阈值为新表长度的0.75倍
                    // n << 1 = 2n, n >>> 1 = n/2, 2n - n/2 = 1.5n = 0.75 * 2n
                    sizeCtl = (n << 1) - (n >>> 1);
                    return;                      // 迁移完成，返回
                }

                // 尝试更新sizeCtl，表示当前线程完成迁移任务
                if (U.compareAndSetInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
                    // 如果不是最后一个线程，直接返回
                    // resizeStamp(n) << RESIZE_STAMP_SHIFT 是扩容时的sizeCtl初始值
                    // sc - 2 表示当前参与的线程数减1
                    if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
                        return;
                    // 如果是最后一个线程，设置finishing标志，重新检查一遍
                    finishing = advance = true;
                    i = n; // 重新检查整个表，确保所有桶都迁移完成
                }
            }
            // 第六步：处理具体的桶
            // 情况1：当前桶为空
            else if ((f = tabAt(tab, i)) == null)
                // 尝试将空桶设置为转发节点
                advance = casTabAt(tab, i, null, fwd);
                // 情况2：当前桶已经处理过了（hash值为MOVED表示已迁移）
            else if ((fh = f.hash) == MOVED)
                advance = true; // 已经处理过，继续下一个
                // 情况3：需要处理当前桶
            else {
                // 对当前桶的头节点加锁，保证线程安全
                synchronized (f) {
                    // 再次检查头节点是否被修改（double-check）
                    if (tabAt(tab, i) == f) {
                        Node<K,V> ln, hn; // ln：低位链表，hn：高位链表

                        // 处理普通链表节点（hash值大于等于0）
                        if (fh >= 0) {
                            // 计算节点在新表中的位置：根据hash值与旧表长度n进行与运算
                            // 如果结果为0，则留在原索引i；如果不为0，则移到i+n
                            int runBit = fh & n;  // 取第一个节点的hash与n的结果
                            Node<K,V> lastRun = f; // 最后一个需要改变位置的节点

                            // 遍历链表，找到最后一个需要改变位置的节点
                            // 这样做是为了避免创建过多的新节点，可以直接复用lastRun之后的链表
                            for (Node<K,V> p = f.next; p != null; p = p.next) {
                                int b = p.hash & n; // 计算当前节点的位置标志
                                if (b != runBit) {   // 如果位置标志发生变化
                                    runBit = b;      // 更新runBit
                                    lastRun = p;      // 更新lastRun
                                }
                            }

                            // 根据最后一个需要改变位置的节点的位置标志，构建高低位链表
                            if (runBit == 0) {        // 应该留在低位
                                ln = lastRun;          // ln指向lastRun开始的链表
                                hn = null;              // 高位链表为空
                            }
                            else {                     // 应该移到高位
                                hn = lastRun;           // hn指向lastRun开始的链表
                                ln = null;               // 低位链表为空
                            }

                            // 遍历lastRun之前的节点，采用头插法构建新的链表
                            for (Node<K,V> p = f; p != lastRun; p = p.next) {
                                int ph = p.hash;
                                K pk = p.key;
                                V pv = p.val;
                                // 根据hash与n的结果决定放入低位还是高位链表
                                if ((ph & n) == 0)      // 应该留在低位
                                    ln = new Node<K,V>(ph, pk, pv, ln);
                                else                     // 应该移到高位
                                    hn = new Node<K,V>(ph, pk, pv, hn);
                            }
                            // 将低位链表放入新表的i位置
                            setTabAt(nextTab, i, ln);
                            // 将高位链表放入新表的i+n位置
                            setTabAt(nextTab, i + n, hn);
                            // 在旧表的i位置设置转发节点，表示已迁移完成
                            setTabAt(tab, i, fwd);
                            // 继续处理下一个桶
                            advance = true;
                        }
                        // 处理红黑树节点
                        else if (f instanceof TreeBin) {
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> lo = null, loTail = null;  // 低位链表的头和尾
                            TreeNode<K,V> hi = null, hiTail = null;  // 高位链表的头和尾
                            int lc = 0, hc = 0;  // 低位和高位链表的节点计数

                            // 遍历树中的所有节点
                            for (Node<K,V> e = t.first; e != null; e = e.next) {
                                int h = e.hash;
                                // 创建新的树节点
                                TreeNode<K,V> p = new TreeNode<K,V>
                                        (h, e.key, e.val, null, null);
                                // 根据hash与n的结果决定放入低位还是高位链表
                                if ((h & n) == 0) {  // 留在低位
                                    if ((p.prev = loTail) == null)
                                        lo = p;      // 第一个节点
                                    else
                                        loTail.next = p; // 链接到尾部
                                    loTail = p;      // 移动尾指针
                                    ++lc;            // 低位节点计数加1
                                }
                                else {               // 移到高位
                                    if ((p.prev = hiTail) == null)
                                        hi = p;      // 第一个节点
                                    else
                                        hiTail.next = p; // 链接到尾部
                                    hiTail = p;      // 移动尾指针
                                    ++hc;            // 高位节点计数加1
                                }
                            }

                            // 根据节点数决定是否需要将树转换回链表
                            // 如果低位链表节点数小于等于反树化阈值（6），则转换为链表
                            ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                                    (hc != 0) ? new TreeBin<K,V>(lo) : t;
                            // 如果高位链表节点数小于等于反树化阈值（6），则转换为链表
                            hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                                    (lc != 0) ? new TreeBin<K,V>(hi) : t;

                            // 将处理后的低位结构放入新表的i位置
                            setTabAt(nextTab, i, ln);
                            // 将处理后的高位结构放入新表的i+n位置
                            setTabAt(nextTab, i + n, hn);
                            // 在旧表的i位置设置转发节点，表示已迁移完成
                            setTabAt(tab, i, fwd);
                            // 继续处理下一个桶
                            advance = true;
                        }
                        // 处理预留节点（这种情况不应该发生）
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
            }
        }
    }

    /* ---------------- Counter support -------------- */

    /**
     * A padded cell for distributing counts.  Adapted from LongAdder
     * and Striped64.  See their internal docs for explanation.
     */
    @jdk.internal.vm.annotation.Contended static final class CounterCell {
        volatile long value;
        CounterCell(long x) { value = x; }
    }

    final long sumCount() {
        CounterCell[] cs = counterCells;// 获取当前映射中使用的计数器单元格数组
        long sum = baseCount;// 获取基础计数值
        if (cs != null) {
            for (CounterCell c : cs)// 遍历所有计数器单元格
                if (c != null)
                    sum += c.value;// 累加当前计数器单元格的值
        }
        return sum;// 返回累加后的总计数
    }

    // See LongAdder version for explanation
    private final void fullAddCount(long x, boolean wasUncontended) {
        int h;
        if ((h = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();      // force initialization
            h = ThreadLocalRandom.getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            CounterCell[] cs; CounterCell c; int n; long v;
            if ((cs = counterCells) != null && (n = cs.length) > 0) {
                if ((c = cs[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {            // Try to attach new Cell
                        CounterCell r = new CounterCell(x); // Optimistic create
                        if (cellsBusy == 0 &&
                                U.compareAndSetInt(this, CELLSBUSY, 0, 1)) {
                            boolean created = false;
                            try {               // Recheck under lock
                                CounterCell[] rs; int m, j;
                                if ((rs = counterCells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (U.compareAndSetLong(c, CELLVALUE, v = c.value, v + x))
                    break;
                else if (counterCells != cs || n >= NCPU)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 &&
                        U.compareAndSetInt(this, CELLSBUSY, 0, 1)) {
                    try {
                        if (counterCells == cs) // Expand table unless stale
                            counterCells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = ThreadLocalRandom.advanceProbe(h);
            }
            else if (cellsBusy == 0 && counterCells == cs &&
                    U.compareAndSetInt(this, CELLSBUSY, 0, 1)) {
                boolean init = false;
                try {                           // Initialize table
                    if (counterCells == cs) {
                        CounterCell[] rs = new CounterCell[2];
                        rs[h & 1] = new CounterCell(x);
                        counterCells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            else if (U.compareAndSetLong(this, BASECOUNT, v = baseCount, v + x))
                break;                          // Fall back on using base
        }
    }

    /* ---------------- Conversion from/to TreeBins -------------- */

    /**
     * Replaces all linked nodes in bin at given index unless table is
     * too small, in which case resizes instead.
     */
    private final void treeifyBin(Node<K,V>[] tab, int index) {
        Node<K,V> b; int n;
        if (tab != null) {
            if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
                tryPresize(n << 1);
            else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
                synchronized (b) {
                    if (tabAt(tab, index) == b) {
                        TreeNode<K,V> hd = null, tl = null;
                        for (Node<K,V> e = b; e != null; e = e.next) {
                            TreeNode<K,V> p =
                                    new TreeNode<K,V>(e.hash, e.key, e.val,
                                            null, null);
                            if ((p.prev = tl) == null)
                                hd = p;
                            else
                                tl.next = p;
                            tl = p;
                        }
                        setTabAt(tab, index, new TreeBin<K,V>(hd));
                    }
                }
            }
        }
    }

    /**
     * Returns a list of non-TreeNodes replacing those in given list.
     */
    static <K,V> Node<K,V> untreeify(Node<K,V> b) {
        Node<K,V> hd = null, tl = null;
        for (Node<K,V> q = b; q != null; q = q.next) {
            Node<K,V> p = new Node<K,V>(q.hash, q.key, q.val);
            if (tl == null)
                hd = p;
            else
                tl.next = p;
            tl = p;
        }
        return hd;
    }

    /* ---------------- TreeNodes -------------- */

    /**
     * Nodes for use in TreeBins.
     */
    static final class TreeNode<K,V> extends Node<K,V> {
        TreeNode<K,V> parent;  // red-black tree links
        TreeNode<K,V> left;
        TreeNode<K,V> right;
        TreeNode<K,V> prev;    // needed to unlink next upon deletion
        boolean red;

        TreeNode(int hash, K key, V val, Node<K,V> next,
                 TreeNode<K,V> parent) {
            super(hash, key, val, next);
            this.parent = parent;
        }

        Node<K,V> find(int h, Object k) {
            return findTreeNode(h, k, null);
        }

        /**
         * Returns the TreeNode (or null if not found) for the given key
         * starting at given root.
         */
        final TreeNode<K,V> findTreeNode(int h, Object k, Class<?> kc) {
            if (k != null) {
                TreeNode<K,V> p = this;
                do {
                    int ph, dir; K pk; TreeNode<K,V> q;
                    TreeNode<K,V> pl = p.left, pr = p.right;
                    if ((ph = p.hash) > h)
                        p = pl;
                    else if (ph < h)
                        p = pr;
                    else if ((pk = p.key) == k || (pk != null && k.equals(pk)))
                        return p;
                    else if (pl == null)
                        p = pr;
                    else if (pr == null)
                        p = pl;
                    else if ((kc != null ||
                            (kc = comparableClassFor(k)) != null) &&
                            (dir = compareComparables(kc, k, pk)) != 0)
                        p = (dir < 0) ? pl : pr;
                    else if ((q = pr.findTreeNode(h, k, kc)) != null)
                        return q;
                    else
                        p = pl;
                } while (p != null);
            }
            return null;
        }
    }

    /* ---------------- TreeBins -------------- */

    /**
     * TreeNodes used at the heads of bins. TreeBins do not hold user
     * keys or values, but instead point to list of TreeNodes and
     * their root. They also maintain a parasitic read-write lock
     * forcing writers (who hold bin lock) to wait for readers (who do
     * not) to complete before tree restructuring operations.
     */
    static final class TreeBin<K,V> extends Node<K,V> {
        TreeNode<K,V> root;
        volatile TreeNode<K,V> first;
        volatile Thread waiter;
        volatile int lockState;
        // values for lockState
        static final int WRITER = 1; // set while holding write lock
        static final int WAITER = 2; // set when waiting for write lock
        static final int READER = 4; // increment value for setting read lock

        /**
         * Tie-breaking utility for ordering insertions when equal
         * hashCodes and non-comparable. We don't require a total
         * order, just a consistent insertion rule to maintain
         * equivalence across rebalancings. Tie-breaking further than
         * necessary simplifies testing a bit.
         */
        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null ||
                    (d = a.getClass().getName().
                            compareTo(b.getClass().getName())) == 0)
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
                        -1 : 1);
            return d;
        }

        /**
         * Creates bin with initial set of nodes headed by b.
         */
        TreeBin(TreeNode<K,V> b) {
            super(TREEBIN, null, null);
            this.first = b;
            TreeNode<K,V> r = null;
            for (TreeNode<K,V> x = b, next; x != null; x = next) {
                next = (TreeNode<K,V>)x.next;
                x.left = x.right = null;
                if (r == null) {
                    x.parent = null;
                    x.red = false;
                    r = x;
                }
                else {
                    K k = x.key;
                    int h = x.hash;
                    Class<?> kc = null;
                    for (TreeNode<K,V> p = r;;) {
                        int dir, ph;
                        K pk = p.key;
                        if ((ph = p.hash) > h)
                            dir = -1;
                        else if (ph < h)
                            dir = 1;
                        else if ((kc == null &&
                                (kc = comparableClassFor(k)) == null) ||
                                (dir = compareComparables(kc, k, pk)) == 0)
                            dir = tieBreakOrder(k, pk);
                        TreeNode<K,V> xp = p;
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            x.parent = xp;
                            if (dir <= 0)
                                xp.left = x;
                            else
                                xp.right = x;
                            r = balanceInsertion(r, x);
                            break;
                        }
                    }
                }
            }
            this.root = r;
            assert checkInvariants(root);
        }

        /**
         * Acquires write lock for tree restructuring.
         */
        private final void lockRoot() {
            if (!U.compareAndSetInt(this, LOCKSTATE, 0, WRITER))
                contendedLock(); // offload to separate method
        }

        /**
         * Releases write lock for tree restructuring.
         */
        private final void unlockRoot() {
            lockState = 0;
        }

        /**
         * Possibly blocks awaiting root lock.
         */
        private final void contendedLock() {
            Thread current = Thread.currentThread(), w;
            for (int s;;) {
                if (((s = lockState) & ~WAITER) == 0) {
                    if (U.compareAndSetInt(this, LOCKSTATE, s, WRITER)) {
                        if (waiter == current)
                            U.compareAndSetReference(this, WAITERTHREAD, current, null);
                        return;
                    }
                }
                else if ((s & WAITER) == 0)
                    U.compareAndSetInt(this, LOCKSTATE, s, s | WAITER);
                else if ((w = waiter) == null)
                    U.compareAndSetReference(this, WAITERTHREAD, null, current);
                else if (w == current)
                    LockSupport.park(this);
            }
        }

        /**
         * Returns matching node or null if none. Tries to search
         * using tree comparisons from root, but continues linear
         * search when lock not available.
         */
        final Node<K,V> find(int h, Object k) {
            if (k != null) {
                for (Node<K,V> e = first; e != null; ) {
                    int s; K ek;
                    if (((s = lockState) & (WAITER|WRITER)) != 0) {
                        if (e.hash == h &&
                                ((ek = e.key) == k || (ek != null && k.equals(ek))))
                            return e;
                        e = e.next;
                    }
                    else if (U.compareAndSetInt(this, LOCKSTATE, s,
                            s + READER)) {
                        TreeNode<K,V> r, p;
                        try {
                            p = ((r = root) == null ? null :
                                    r.findTreeNode(h, k, null));
                        } finally {
                            Thread w;
                            if (U.getAndAddInt(this, LOCKSTATE, -READER) ==
                                    (READER|WAITER) && (w = waiter) != null)
                                LockSupport.unpark(w);
                        }
                        return p;
                    }
                }
            }
            return null;
        }

        /**
         * Finds or adds a node.
         * @return null if added
         */
        final TreeNode<K,V> putTreeVal(int h, K k, V v) {
            Class<?> kc = null;
            boolean searched = false;
            for (TreeNode<K,V> p = root;;) {
                int dir, ph; K pk;
                if (p == null) {
                    first = root = new TreeNode<K,V>(h, k, v, null, null);
                    break;
                }
                else if ((ph = p.hash) > h)
                    dir = -1;
                else if (ph < h)
                    dir = 1;
                else if ((pk = p.key) == k || (pk != null && k.equals(pk)))
                    return p;
                else if ((kc == null &&
                        (kc = comparableClassFor(k)) == null) ||
                        (dir = compareComparables(kc, k, pk)) == 0) {
                    if (!searched) {
                        TreeNode<K,V> q, ch;
                        searched = true;
                        if (((ch = p.left) != null &&
                                (q = ch.findTreeNode(h, k, kc)) != null) ||
                                ((ch = p.right) != null &&
                                        (q = ch.findTreeNode(h, k, kc)) != null))
                            return q;
                    }
                    dir = tieBreakOrder(k, pk);
                }

                TreeNode<K,V> xp = p;
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    TreeNode<K,V> x, f = first;
                    first = x = new TreeNode<K,V>(h, k, v, f, xp);
                    if (f != null)
                        f.prev = x;
                    if (dir <= 0)
                        xp.left = x;
                    else
                        xp.right = x;
                    if (!xp.red)
                        x.red = true;
                    else {
                        lockRoot();
                        try {
                            root = balanceInsertion(root, x);
                        } finally {
                            unlockRoot();
                        }
                    }
                    break;
                }
            }
            assert checkInvariants(root);
            return null;
        }

        /**
         * Removes the given node, that must be present before this
         * call.  This is messier than typical red-black deletion code
         * because we cannot swap the contents of an interior node
         * with a leaf successor that is pinned by "next" pointers
         * that are accessible independently of lock. So instead we
         * swap the tree linkages.
         *
         * @return true if now too small, so should be untreeified
         */
        final boolean removeTreeNode(TreeNode<K,V> p) {
            TreeNode<K,V> next = (TreeNode<K,V>)p.next;
            TreeNode<K,V> pred = p.prev;  // unlink traversal pointers
            TreeNode<K,V> r, rl;
            if (pred == null)
                first = next;
            else
                pred.next = next;
            if (next != null)
                next.prev = pred;
            if (first == null) {
                root = null;
                return true;
            }
            if ((r = root) == null || r.right == null || // too small
                    (rl = r.left) == null || rl.left == null)
                return true;
            lockRoot();
            try {
                TreeNode<K,V> replacement;
                TreeNode<K,V> pl = p.left;
                TreeNode<K,V> pr = p.right;
                if (pl != null && pr != null) {
                    TreeNode<K,V> s = pr, sl;
                    while ((sl = s.left) != null) // find successor
                        s = sl;
                    boolean c = s.red; s.red = p.red; p.red = c; // swap colors
                    TreeNode<K,V> sr = s.right;
                    TreeNode<K,V> pp = p.parent;
                    if (s == pr) { // p was s's direct parent
                        p.parent = s;
                        s.right = p;
                    }
                    else {
                        TreeNode<K,V> sp = s.parent;
                        if ((p.parent = sp) != null) {
                            if (s == sp.left)
                                sp.left = p;
                            else
                                sp.right = p;
                        }
                        if ((s.right = pr) != null)
                            pr.parent = s;
                    }
                    p.left = null;
                    if ((p.right = sr) != null)
                        sr.parent = p;
                    if ((s.left = pl) != null)
                        pl.parent = s;
                    if ((s.parent = pp) == null)
                        r = s;
                    else if (p == pp.left)
                        pp.left = s;
                    else
                        pp.right = s;
                    if (sr != null)
                        replacement = sr;
                    else
                        replacement = p;
                }
                else if (pl != null)
                    replacement = pl;
                else if (pr != null)
                    replacement = pr;
                else
                    replacement = p;
                if (replacement != p) {
                    TreeNode<K,V> pp = replacement.parent = p.parent;
                    if (pp == null)
                        r = replacement;
                    else if (p == pp.left)
                        pp.left = replacement;
                    else
                        pp.right = replacement;
                    p.left = p.right = p.parent = null;
                }

                root = (p.red) ? r : balanceDeletion(r, replacement);

                if (p == replacement) {  // detach pointers
                    TreeNode<K,V> pp;
                    if ((pp = p.parent) != null) {
                        if (p == pp.left)
                            pp.left = null;
                        else if (p == pp.right)
                            pp.right = null;
                        p.parent = null;
                    }
                }
            } finally {
                unlockRoot();
            }
            assert checkInvariants(root);
            return false;
        }

        /* ------------------------------------------------------------ */
        // Red-black tree methods, all adapted from CLR

        static <K,V> TreeNode<K,V> rotateLeft(TreeNode<K,V> root,
                                              TreeNode<K,V> p) {
            TreeNode<K,V> r, pp, rl;
            if (p != null && (r = p.right) != null) {
                if ((rl = p.right = r.left) != null)
                    rl.parent = p;
                if ((pp = r.parent = p.parent) == null)
                    (root = r).red = false;
                else if (pp.left == p)
                    pp.left = r;
                else
                    pp.right = r;
                r.left = p;
                p.parent = r;
            }
            return root;
        }

        static <K,V> TreeNode<K,V> rotateRight(TreeNode<K,V> root,
                                               TreeNode<K,V> p) {
            TreeNode<K,V> l, pp, lr;
            if (p != null && (l = p.left) != null) {
                if ((lr = p.left = l.right) != null)
                    lr.parent = p;
                if ((pp = l.parent = p.parent) == null)
                    (root = l).red = false;
                else if (pp.right == p)
                    pp.right = l;
                else
                    pp.left = l;
                l.right = p;
                p.parent = l;
            }
            return root;
        }

        static <K,V> TreeNode<K,V> balanceInsertion(TreeNode<K,V> root,
                                                    TreeNode<K,V> x) {
            x.red = true;
            for (TreeNode<K,V> xp, xpp, xppl, xppr;;) {
                if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                }
                else if (!xp.red || (xpp = xp.parent) == null)
                    return root;
                if (xp == (xppl = xpp.left)) {
                    if ((xppr = xpp.right) != null && xppr.red) {
                        xppr.red = false;
                        xp.red = false;
                        xpp.red = true;
                        x = xpp;
                    }
                    else {
                        if (x == xp.right) {
                            root = rotateLeft(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateRight(root, xpp);
                            }
                        }
                    }
                }
                else {
                    if (xppl != null && xppl.red) {
                        xppl.red = false;
                        xp.red = false;
                        xpp.red = true;
                        x = xpp;
                    }
                    else {
                        if (x == xp.left) {
                            root = rotateRight(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateLeft(root, xpp);
                            }
                        }
                    }
                }
            }
        }

        static <K,V> TreeNode<K,V> balanceDeletion(TreeNode<K,V> root,
                                                   TreeNode<K,V> x) {
            for (TreeNode<K,V> xp, xpl, xpr;;) {
                if (x == null || x == root)
                    return root;
                else if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                }
                else if (x.red) {
                    x.red = false;
                    return root;
                }
                else if ((xpl = xp.left) == x) {
                    if ((xpr = xp.right) != null && xpr.red) {
                        xpr.red = false;
                        xp.red = true;
                        root = rotateLeft(root, xp);
                        xpr = (xp = x.parent) == null ? null : xp.right;
                    }
                    if (xpr == null)
                        x = xp;
                    else {
                        TreeNode<K,V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) &&
                                (sl == null || !sl.red)) {
                            xpr.red = true;
                            x = xp;
                        }
                        else {
                            if (sr == null || !sr.red) {
                                if (sl != null)
                                    sl.red = false;
                                xpr.red = true;
                                root = rotateRight(root, xpr);
                                xpr = (xp = x.parent) == null ?
                                        null : xp.right;
                            }
                            if (xpr != null) {
                                xpr.red = (xp == null) ? false : xp.red;
                                if ((sr = xpr.right) != null)
                                    sr.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateLeft(root, xp);
                            }
                            x = root;
                        }
                    }
                }
                else { // symmetric
                    if (xpl != null && xpl.red) {
                        xpl.red = false;
                        xp.red = true;
                        root = rotateRight(root, xp);
                        xpl = (xp = x.parent) == null ? null : xp.left;
                    }
                    if (xpl == null)
                        x = xp;
                    else {
                        TreeNode<K,V> sl = xpl.left, sr = xpl.right;
                        if ((sl == null || !sl.red) &&
                                (sr == null || !sr.red)) {
                            xpl.red = true;
                            x = xp;
                        }
                        else {
                            if (sl == null || !sl.red) {
                                if (sr != null)
                                    sr.red = false;
                                xpl.red = true;
                                root = rotateLeft(root, xpl);
                                xpl = (xp = x.parent) == null ?
                                        null : xp.left;
                            }
                            if (xpl != null) {
                                xpl.red = (xp == null) ? false : xp.red;
                                if ((sl = xpl.left) != null)
                                    sl.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateRight(root, xp);
                            }
                            x = root;
                        }
                    }
                }
            }
        }

        /**
         * Checks invariants recursively for the tree of Nodes rooted at t.
         */
        static <K,V> boolean checkInvariants(TreeNode<K,V> t) {
            TreeNode<K,V> tp = t.parent, tl = t.left, tr = t.right,
                    tb = t.prev, tn = (TreeNode<K,V>)t.next;
            if (tb != null && tb.next != t)
                return false;
            if (tn != null && tn.prev != t)
                return false;
            if (tp != null && t != tp.left && t != tp.right)
                return false;
            if (tl != null && (tl.parent != t || tl.hash > t.hash))
                return false;
            if (tr != null && (tr.parent != t || tr.hash < t.hash))
                return false;
            if (t.red && tl != null && tl.red && tr != null && tr.red)
                return false;
            if (tl != null && !checkInvariants(tl))
                return false;
            if (tr != null && !checkInvariants(tr))
                return false;
            return true;
        }

        private static final long LOCKSTATE
                = U.objectFieldOffset(TreeBin.class, "lockState");
        private static final long WAITERTHREAD
                = U.objectFieldOffset(TreeBin.class, "waiter");
    }

    /* ----------------Table Traversal -------------- */

    /**
     * Records the table, its length, and current traversal index for a
     * traverser that must process a region of a forwarded table before
     * proceeding with current table.
     */
    static final class TableStack<K,V> {
        int length;
        int index;
        Node<K,V>[] tab;
        TableStack<K,V> next;
    }

    /**
     * 封装了用于 containsValue 等方法的遍历操作；
     * 也作为其他迭代器和分割器的基类。
     *
     * advance 方法会访问在迭代器构造时可达的每个仍然有效的节点一次。
     * 它可能会错过一些在访问桶之后添加到该桶的节点，这对于一致性保证来说是可以接受的。
     * 在可能发生的持续扩容情况下维护这个属性，需要大量的状态记录，
     * 这些状态在 volatile 访问中很难优化掉。即便如此，遍历仍保持合理的吞吐量。
     */
    static class Traverser<K,V> {
        Node<K,V>[] tab;        // 当前表；如果扩容则更新
        Node<K,V> next;         // 下一个要使用的条目
        TableStack<K,V> stack, spare; // 用于在 ForwardingNodes 上保存/恢复状态
        int index;              // 下一个要使用的桶索引
        int baseIndex;          // 初始表的当前索引
        int baseLimit;          // 初始表的索引边界
        final int baseSize;     // 初始表的大小

        Traverser(Node<K,V>[] tab, int size, int index, int limit) {
            this.tab = tab;
            this.baseSize = size;
            this.baseIndex = this.index = index;
            this.baseLimit = limit;
            this.next = null;
        }

        /**
         * 如果可能，先行，返回下一个有效节点，若无则返回空节点
         */
        final Node<K,V> advance() {
            Node<K,V> e;
            // 1. 如果当前有下一个节点，先尝试获取它的下一个节点
            if ((e = next) != null)
                e = e.next;
            // 2. 主循环：遍历所有桶和节点
            for (;;) {
                Node<K,V>[] t; int i, n;  // must use locals in checks

                // 3. 如果当前节点不为空，返回它并更新next
                if (e != null)
                    return next = e;

                // 4. 检查是否遍历完成
                if (baseIndex >= baseLimit || (t = tab) == null ||
                        (n = t.length) <= (i = index) || i < 0)
                    return next = null;

                // 5. 获取当前桶的第一个节点
                if ((e = tabAt(t, i)) != null && e.hash < 0) {
                    // 6. 处理特殊节点
                    if (e instanceof ForwardingNode) {
                        // 6.1 遇到转发节点：跳转到新数组
                        tab = ((ForwardingNode<K,V>)e).nextTable;
                        e = null;
                        pushState(t, i, n);// 保存当前状态
                        continue;
                    }
                    else if (e instanceof TreeBin)
                        // 6.2 遇到树节点：获取树的第一个节点（链表形式）
                        e = ((TreeBin<K,V>)e).first;
                    else
                        // 6.3 其他特殊节点（如 ReservationNode）
                        e = null;
                }
                // 7. 处理栈状态恢复
                if (stack != null)
                    recoverState(n);
                    // 8. 移动到下一个桶（处理扩容后的分段遍历）
                else if ((index = i + baseSize) >= n)
                    index = ++baseIndex; // 如果当前分段遍历完，访问上层槽位
            }
        }

        /**
         * Saves traversal state upon encountering a forwarding node.
         */
        private void pushState(Node<K,V>[] t, int i, int n) {
            TableStack<K,V> s = spare;  // reuse if possible
            if (s != null)
                spare = s.next;
            else
                s = new TableStack<K,V>();
            s.tab = t;
            s.length = n;
            s.index = i;
            s.next = stack;
            stack = s;
        }

        /**
         * Possibly pops traversal state.
         *
         * @param n length of current table
         */
        private void recoverState(int n) {
            TableStack<K,V> s; int len;
            while ((s = stack) != null && (index += (len = s.length)) >= n) {
                n = len;
                index = s.index;
                tab = s.tab;
                s.tab = null;
                TableStack<K,V> next = s.next;
                s.next = spare; // save for reuse
                stack = next;
                spare = s;
            }
            if (s == null && (index += baseSize) >= n)
                index = ++baseIndex;
        }
    }

    /**
     * Base of key, value, and entry Iterators. Adds fields to
     * Traverser to support iterator.remove.
     */
    static class BaseIterator<K,V> extends Traverser<K,V> {
        final ConcurrentHashMap<K,V> map;
        Node<K,V> lastReturned;
        BaseIterator(Node<K,V>[] tab, int size, int index, int limit,
                     ConcurrentHashMap<K,V> map) {
            super(tab, size, index, limit);
            this.map = map;
            advance();
        }

        public final boolean hasNext() { return next != null; }
        public final boolean hasMoreElements() { return next != null; }

        public final void remove() {
            Node<K,V> p;
            if ((p = lastReturned) == null)
                throw new IllegalStateException();
            lastReturned = null;
            map.replaceNode(p.key, null, null);
        }
    }

    static final class KeyIterator<K,V> extends BaseIterator<K,V>
            implements Iterator<K>, Enumeration<K> {
        KeyIterator(Node<K,V>[] tab, int size, int index, int limit,
                    ConcurrentHashMap<K,V> map) {
            super(tab, size, index, limit, map);
        }

        public final K next() {
            Node<K,V> p;
            if ((p = next) == null)
                throw new NoSuchElementException();
            K k = p.key;
            lastReturned = p;
            advance();
            return k;
        }

        public final K nextElement() { return next(); }
    }

    static final class ValueIterator<K,V> extends BaseIterator<K,V>
            implements Iterator<V>, Enumeration<V> {
        ValueIterator(Node<K,V>[] tab, int size, int index, int limit,
                      ConcurrentHashMap<K,V> map) {
            super(tab, size, index, limit, map);
        }

        public final V next() {
            Node<K,V> p;
            if ((p = next) == null)
                throw new NoSuchElementException();
            V v = p.val;
            lastReturned = p;
            advance();
            return v;
        }

        public final V nextElement() { return next(); }
    }

    static final class EntryIterator<K,V> extends BaseIterator<K,V>
            implements Iterator<Map.Entry<K,V>> {
        EntryIterator(Node<K,V>[] tab, int size, int index, int limit,
                      ConcurrentHashMap<K,V> map) {
            super(tab, size, index, limit, map);
        }

        public final Map.Entry<K,V> next() {
            Node<K,V> p;
            if ((p = next) == null)
                throw new NoSuchElementException();
            K k = p.key;
            V v = p.val;
            lastReturned = p;
            advance();
            return new MapEntry<K,V>(k, v, map);
        }
    }

    /**
     * Exported Entry for EntryIterator.
     */
    static final class MapEntry<K,V> implements Map.Entry<K,V> {
        final K key; // non-null
        V val;       // non-null
        final ConcurrentHashMap<K,V> map;
        MapEntry(K key, V val, ConcurrentHashMap<K,V> map) {
            this.key = key;
            this.val = val;
            this.map = map;
        }
        public K getKey()        { return key; }
        public V getValue()      { return val; }
        public int hashCode()    { return key.hashCode() ^ val.hashCode(); }
        public String toString() {
            return Helpers.mapEntryToString(key, val);
        }

        public boolean equals(Object o) {
            Object k, v; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    (k == key || k.equals(key)) &&
                    (v == val || v.equals(val)));
        }

        /**
         * Sets our entry's value and writes through to the map. The
         * value to return is somewhat arbitrary here. Since we do not
         * necessarily track asynchronous changes, the most recent
         * "previous" value could be different from what we return (or
         * could even have been removed, in which case the put will
         * re-establish). We do not and cannot guarantee more.
         */
        public V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = val;
            val = value;
            map.put(key, value);
            return v;
        }
    }

    static final class KeySpliterator<K,V> extends Traverser<K,V>
            implements Spliterator<K> {
        long est;               // size estimate
        KeySpliterator(Node<K,V>[] tab, int size, int index, int limit,
                       long est) {
            super(tab, size, index, limit);
            this.est = est;
        }

        public KeySpliterator<K,V> trySplit() {
            int i, f, h;
            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null :
                    new KeySpliterator<K,V>(tab, baseSize, baseLimit = h,
                            f, est >>>= 1);
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null) throw new NullPointerException();
            for (Node<K,V> p; (p = advance()) != null;)
                action.accept(p.key);
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            if (action == null) throw new NullPointerException();
            Node<K,V> p;
            if ((p = advance()) == null)
                return false;
            action.accept(p.key);
            return true;
        }

        public long estimateSize() { return est; }

        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.CONCURRENT |
                    Spliterator.NONNULL;
        }
    }

    static final class ValueSpliterator<K,V> extends Traverser<K,V>
            implements Spliterator<V> {
        long est;               // size estimate
        ValueSpliterator(Node<K,V>[] tab, int size, int index, int limit,
                         long est) {
            super(tab, size, index, limit);
            this.est = est;
        }

        public ValueSpliterator<K,V> trySplit() {
            int i, f, h;
            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null :
                    new ValueSpliterator<K,V>(tab, baseSize, baseLimit = h,
                            f, est >>>= 1);
        }

        public void forEachRemaining(Consumer<? super V> action) {
            if (action == null) throw new NullPointerException();
            for (Node<K,V> p; (p = advance()) != null;)
                action.accept(p.val);
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            if (action == null) throw new NullPointerException();
            Node<K,V> p;
            if ((p = advance()) == null)
                return false;
            action.accept(p.val);
            return true;
        }

        public long estimateSize() { return est; }

        public int characteristics() {
            return Spliterator.CONCURRENT | Spliterator.NONNULL;
        }
    }

    static final class EntrySpliterator<K,V> extends Traverser<K,V>
            implements Spliterator<Map.Entry<K,V>> {
        final ConcurrentHashMap<K,V> map; // To export MapEntry
        long est;               // size estimate
        EntrySpliterator(Node<K,V>[] tab, int size, int index, int limit,
                         long est, ConcurrentHashMap<K,V> map) {
            super(tab, size, index, limit);
            this.map = map;
            this.est = est;
        }

        public EntrySpliterator<K,V> trySplit() {
            int i, f, h;
            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null :
                    new EntrySpliterator<K,V>(tab, baseSize, baseLimit = h,
                            f, est >>>= 1, map);
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K,V>> action) {
            if (action == null) throw new NullPointerException();
            for (Node<K,V> p; (p = advance()) != null; )
                action.accept(new MapEntry<K,V>(p.key, p.val, map));
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> action) {
            if (action == null) throw new NullPointerException();
            Node<K,V> p;
            if ((p = advance()) == null)
                return false;
            action.accept(new MapEntry<K,V>(p.key, p.val, map));
            return true;
        }

        public long estimateSize() { return est; }

        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.CONCURRENT |
                    Spliterator.NONNULL;
        }
    }

    // Parallel bulk operations

    /**
     * Computes initial batch value for bulk tasks. The returned value
     * is approximately exp2 of the number of times (minus one) to
     * split task by two before executing leaf action. This value is
     * faster to compute and more convenient to use as a guide to
     * splitting than is the depth, since it is used while dividing by
     * two anyway.
     */
    final int batchFor(long b) {
        long n;
        if (b == Long.MAX_VALUE || (n = sumCount()) <= 1L || n < b)
            return 0;
        int sp = ForkJoinPool.getCommonPoolParallelism() << 2; // slack of 4
        return (b <= 0L || (n /= b) >= sp) ? sp : (int)n;
    }

    /**
     * Performs the given action for each (key, value).
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param action the action
     * @since 1.8
     */
    public void forEach(long parallelismThreshold,
                        BiConsumer<? super K,? super V> action) {
        if (action == null) throw new NullPointerException();
        new ForEachMappingTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each (key, value).
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     * @param <U> the return type of the transformer
     * @since 1.8
     */
    public <U> void forEach(long parallelismThreshold,
                            BiFunction<? super K, ? super V, ? extends U> transformer,
                            Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        new ForEachTransformedMappingTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each (key, value), or null if none.  Upon
     * success, further element processing is suppressed and the
     * results of any other parallel invocations of the search
     * function are ignored.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @param <U> the return type of the search function
     * @return a non-null result from applying the given search
     * function on each (key, value), or null if none
     * @since 1.8
     */
    public <U> U search(long parallelismThreshold,
                        BiFunction<? super K, ? super V, ? extends U> searchFunction) {
        if (searchFunction == null) throw new NullPointerException();
        return new SearchMappingsTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        searchFunction, new AtomicReference<U>()).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, or null if none.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @param <U> the return type of the transformer
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     * @since 1.8
     */
    public <U> U reduce(long parallelismThreshold,
                        BiFunction<? super K, ? super V, ? extends U> transformer,
                        BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceMappingsTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     * @since 1.8
     */
    public double reduceToDouble(long parallelismThreshold,
                                 ToDoubleBiFunction<? super K, ? super V> transformer,
                                 double basis,
                                 DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceMappingsToDoubleTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     * @since 1.8
     */
    public long reduceToLong(long parallelismThreshold,
                             ToLongBiFunction<? super K, ? super V> transformer,
                             long basis,
                             LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceMappingsToLongTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all (key, value) pairs using the given reducer to
     * combine values, and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all (key, value) pairs
     * @since 1.8
     */
    public int reduceToInt(long parallelismThreshold,
                           ToIntBiFunction<? super K, ? super V> transformer,
                           int basis,
                           IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceMappingsToIntTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Performs the given action for each key.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param action the action
     * @since 1.8
     */
    public void forEachKey(long parallelismThreshold,
                           Consumer<? super K> action) {
        if (action == null) throw new NullPointerException();
        new ForEachKeyTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each key.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     * @param <U> the return type of the transformer
     * @since 1.8
     */
    public <U> void forEachKey(long parallelismThreshold,
                               Function<? super K, ? extends U> transformer,
                               Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        new ForEachTransformedKeyTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each key, or null if none. Upon success,
     * further element processing is suppressed and the results of
     * any other parallel invocations of the search function are
     * ignored.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @param <U> the return type of the search function
     * @return a non-null result from applying the given search
     * function on each key, or null if none
     * @since 1.8
     */
    public <U> U searchKeys(long parallelismThreshold,
                            Function<? super K, ? extends U> searchFunction) {
        if (searchFunction == null) throw new NullPointerException();
        return new SearchKeysTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        searchFunction, new AtomicReference<U>()).invoke();
    }

    /**
     * Returns the result of accumulating all keys using the given
     * reducer to combine values, or null if none.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all keys using the given
     * reducer to combine values, or null if none
     * @since 1.8
     */
    public K reduceKeys(long parallelismThreshold,
                        BiFunction<? super K, ? super K, ? extends K> reducer) {
        if (reducer == null) throw new NullPointerException();
        return new ReduceKeysTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, or
     * null if none.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @param <U> the return type of the transformer
     * @return the result of accumulating the given transformation
     * of all keys
     * @since 1.8
     */
    public <U> U reduceKeys(long parallelismThreshold,
                            Function<? super K, ? extends U> transformer,
                            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceKeysTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     * @since 1.8
     */
    public double reduceKeysToDouble(long parallelismThreshold,
                                     ToDoubleFunction<? super K> transformer,
                                     double basis,
                                     DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceKeysToDoubleTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     * @since 1.8
     */
    public long reduceKeysToLong(long parallelismThreshold,
                                 ToLongFunction<? super K> transformer,
                                 long basis,
                                 LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceKeysToLongTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all keys using the given reducer to combine values, and
     * the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all keys
     * @since 1.8
     */
    public int reduceKeysToInt(long parallelismThreshold,
                               ToIntFunction<? super K> transformer,
                               int basis,
                               IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceKeysToIntTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Performs the given action for each value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param action the action
     * @since 1.8
     */
    public void forEachValue(long parallelismThreshold,
                             Consumer<? super V> action) {
        if (action == null)
            throw new NullPointerException();
        new ForEachValueTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     * @param <U> the return type of the transformer
     * @since 1.8
     */
    public <U> void forEachValue(long parallelismThreshold,
                                 Function<? super V, ? extends U> transformer,
                                 Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        new ForEachTransformedValueTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each value, or null if none.  Upon success,
     * further element processing is suppressed and the results of
     * any other parallel invocations of the search function are
     * ignored.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @param <U> the return type of the search function
     * @return a non-null result from applying the given search
     * function on each value, or null if none
     * @since 1.8
     */
    public <U> U searchValues(long parallelismThreshold,
                              Function<? super V, ? extends U> searchFunction) {
        if (searchFunction == null) throw new NullPointerException();
        return new SearchValuesTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        searchFunction, new AtomicReference<U>()).invoke();
    }

    /**
     * Returns the result of accumulating all values using the
     * given reducer to combine values, or null if none.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all values
     * @since 1.8
     */
    public V reduceValues(long parallelismThreshold,
                          BiFunction<? super V, ? super V, ? extends V> reducer) {
        if (reducer == null) throw new NullPointerException();
        return new ReduceValuesTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values, or
     * null if none.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @param <U> the return type of the transformer
     * @return the result of accumulating the given transformation
     * of all values
     * @since 1.8
     */
    public <U> U reduceValues(long parallelismThreshold,
                              Function<? super V, ? extends U> transformer,
                              BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceValuesTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     * @since 1.8
     */
    public double reduceValuesToDouble(long parallelismThreshold,
                                       ToDoubleFunction<? super V> transformer,
                                       double basis,
                                       DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceValuesToDoubleTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     * @since 1.8
     */
    public long reduceValuesToLong(long parallelismThreshold,
                                   ToLongFunction<? super V> transformer,
                                   long basis,
                                   LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceValuesToLongTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all values using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all values
     * @since 1.8
     */
    public int reduceValuesToInt(long parallelismThreshold,
                                 ToIntFunction<? super V> transformer,
                                 int basis,
                                 IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceValuesToIntTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Performs the given action for each entry.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param action the action
     * @since 1.8
     */
    public void forEachEntry(long parallelismThreshold,
                             Consumer<? super Map.Entry<K,V>> action) {
        if (action == null) throw new NullPointerException();
        new ForEachEntryTask<K,V>(null, batchFor(parallelismThreshold), 0, 0, table,
                action).invoke();
    }

    /**
     * Performs the given action for each non-null transformation
     * of each entry.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case the action is not applied)
     * @param action the action
     * @param <U> the return type of the transformer
     * @since 1.8
     */
    public <U> void forEachEntry(long parallelismThreshold,
                                 Function<Map.Entry<K,V>, ? extends U> transformer,
                                 Consumer<? super U> action) {
        if (transformer == null || action == null)
            throw new NullPointerException();
        new ForEachTransformedEntryTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        transformer, action).invoke();
    }

    /**
     * Returns a non-null result from applying the given search
     * function on each entry, or null if none.  Upon success,
     * further element processing is suppressed and the results of
     * any other parallel invocations of the search function are
     * ignored.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param searchFunction a function returning a non-null
     * result on success, else null
     * @param <U> the return type of the search function
     * @return a non-null result from applying the given search
     * function on each entry, or null if none
     * @since 1.8
     */
    public <U> U searchEntries(long parallelismThreshold,
                               Function<Map.Entry<K,V>, ? extends U> searchFunction) {
        if (searchFunction == null) throw new NullPointerException();
        return new SearchEntriesTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        searchFunction, new AtomicReference<U>()).invoke();
    }

    /**
     * Returns the result of accumulating all entries using the
     * given reducer to combine values, or null if none.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param reducer a commutative associative combining function
     * @return the result of accumulating all entries
     * @since 1.8
     */
    public Map.Entry<K,V> reduceEntries(long parallelismThreshold,
                                        BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer) {
        if (reducer == null) throw new NullPointerException();
        return new ReduceEntriesTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * or null if none.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element, or null if there is no transformation (in
     * which case it is not combined)
     * @param reducer a commutative associative combining function
     * @param <U> the return type of the transformer
     * @return the result of accumulating the given transformation
     * of all entries
     * @since 1.8
     */
    public <U> U reduceEntries(long parallelismThreshold,
                               Function<Map.Entry<K,V>, ? extends U> transformer,
                               BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceEntriesTask<K,V,U>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     * @since 1.8
     */
    public double reduceEntriesToDouble(long parallelismThreshold,
                                        ToDoubleFunction<Map.Entry<K,V>> transformer,
                                        double basis,
                                        DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceEntriesToDoubleTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     * @since 1.8
     */
    public long reduceEntriesToLong(long parallelismThreshold,
                                    ToLongFunction<Map.Entry<K,V>> transformer,
                                    long basis,
                                    LongBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceEntriesToLongTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }

    /**
     * Returns the result of accumulating the given transformation
     * of all entries using the given reducer to combine values,
     * and the given basis as an identity value.
     *
     * @param parallelismThreshold the (estimated) number of elements
     * needed for this operation to be executed in parallel
     * @param transformer a function returning the transformation
     * for an element
     * @param basis the identity (initial default value) for the reduction
     * @param reducer a commutative associative combining function
     * @return the result of accumulating the given transformation
     * of all entries
     * @since 1.8
     */
    public int reduceEntriesToInt(long parallelismThreshold,
                                  ToIntFunction<Map.Entry<K,V>> transformer,
                                  int basis,
                                  IntBinaryOperator reducer) {
        if (transformer == null || reducer == null)
            throw new NullPointerException();
        return new MapReduceEntriesToIntTask<K,V>
                (null, batchFor(parallelismThreshold), 0, 0, table,
                        null, transformer, basis, reducer).invoke();
    }


    /* ----------------Views -------------- */

    /**
     * Base class for views.
     */
    abstract static class CollectionView<K,V,E>
            implements Collection<E>, java.io.Serializable {
        private static final long serialVersionUID = 7249069246763182397L;
        final ConcurrentHashMap<K,V> map;
        CollectionView(ConcurrentHashMap<K,V> map)  { this.map = map; }

        /**
         * Returns the map backing this view.
         *
         * @return the map backing this view
         */
        public ConcurrentHashMap<K,V> getMap() { return map; }

        /**
         * Removes all of the elements from this view, by removing all
         * the mappings from the map backing this view.
         */
        public final void clear()      { map.clear(); }
        public final int size()        { return map.size(); }
        public final boolean isEmpty() { return map.isEmpty(); }

        // implementations below rely on concrete classes supplying these
        // abstract methods
        /**
         * Returns an iterator over the elements in this collection.
         *
         * <p>The returned iterator is
         * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
         *
         * @return an iterator over the elements in this collection
         */
        public abstract Iterator<E> iterator();
        public abstract boolean contains(Object o);
        public abstract boolean remove(Object o);

        private static final String OOME_MSG = "Required array size too large";

        public final Object[] toArray() {
            long sz = map.mappingCount();
            if (sz > MAX_ARRAY_SIZE)
                throw new OutOfMemoryError(OOME_MSG);
            int n = (int)sz;
            Object[] r = new Object[n];
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError(OOME_MSG);
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
                        n = MAX_ARRAY_SIZE;
                    else
                        n += (n >>> 1) + 1;
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = e;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        @SuppressWarnings("unchecked")
        public final <T> T[] toArray(T[] a) {
            long sz = map.mappingCount();
            if (sz > MAX_ARRAY_SIZE)
                throw new OutOfMemoryError(OOME_MSG);
            int m = (int)sz;
            T[] r = (a.length >= m) ? a :
                    (T[])java.lang.reflect.Array
                            .newInstance(a.getClass().getComponentType(), m);
            int n = r.length;
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError(OOME_MSG);
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
                        n = MAX_ARRAY_SIZE;
                    else
                        n += (n >>> 1) + 1;
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = (T)e;
            }
            if (a == r && i < n) {
                r[i] = null; // null-terminate
                return r;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        /**
         * Returns a string representation of this collection.
         * The string representation consists of the string representations
         * of the collection's elements in the order they are returned by
         * its iterator, enclosed in square brackets ({@code "[]"}).
         * Adjacent elements are separated by the characters {@code ", "}
         * (comma and space).  Elements are converted to strings as by
         * {@link String#valueOf(Object)}.
         *
         * @return a string representation of this collection
         */
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            Iterator<E> it = iterator();
            if (it.hasNext()) {
                for (;;) {
                    Object e = it.next();
                    sb.append(e == this ? "(this Collection)" : e);
                    if (!it.hasNext())
                        break;
                    sb.append(',').append(' ');
                }
            }
            return sb.append(']').toString();
        }

        public final boolean containsAll(Collection<?> c) {
            if (c != this) {
                for (Object e : c) {
                    if (e == null || !contains(e))
                        return false;
                }
            }
            return true;
        }

        public boolean removeAll(Collection<?> c) {
            if (c == null) throw new NullPointerException();
            boolean modified = false;
            // Use (c instanceof Set) as a hint that lookup in c is as
            // efficient as this view
            Node<K,V>[] t;
            if ((t = map.table) == null) {
                return false;
            } else if (c instanceof Set<?> && c.size() > t.length) {
                for (Iterator<?> it = iterator(); it.hasNext(); ) {
                    if (c.contains(it.next())) {
                        it.remove();
                        modified = true;
                    }
                }
            } else {
                for (Object e : c)
                    modified |= remove(e);
            }
            return modified;
        }

        public final boolean retainAll(Collection<?> c) {
            if (c == null) throw new NullPointerException();
            boolean modified = false;
            for (Iterator<E> it = iterator(); it.hasNext();) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

    }

    /**
     * A view of a ConcurrentHashMap as a {@link Set} of keys, in
     * which additions may optionally be enabled by mapping to a
     * common value.  This class cannot be directly instantiated.
     * See {@link #keySet() keySet()},
     * {@link #keySet(Object) keySet(V)},
     * {@link #newKeySet() newKeySet()},
     * {@link #newKeySet(int) newKeySet(int)}.
     *
     * @since 1.8
     */
    public static class KeySetView<K,V> extends CollectionView<K,V,K>
            implements Set<K>, java.io.Serializable {
        private static final long serialVersionUID = 7249069246763182397L;
        @SuppressWarnings("serial") // Conditionally serializable
        private final V value;
        KeySetView(ConcurrentHashMap<K,V> map, V value) {  // non-public
            super(map);
            this.value = value;
        }

        /**
         * Returns the default mapped value for additions,
         * or {@code null} if additions are not supported.
         *
         * @return the default mapped value for additions, or {@code null}
         * if not supported
         */
        public V getMappedValue() { return value; }

        /**
         * {@inheritDoc}
         * @throws NullPointerException if the specified key is null
         */
        public boolean contains(Object o) { return map.containsKey(o); }

        /**
         * Removes the key from this map view, by removing the key (and its
         * corresponding value) from the backing map.  This method does
         * nothing if the key is not in the map.
         *
         * @param  o the key to be removed from the backing map
         * @return {@code true} if the backing map contained the specified key
         * @throws NullPointerException if the specified key is null
         */
        public boolean remove(Object o) { return map.remove(o) != null; }

        /**
         * @return an iterator over the keys of the backing map
         */
        public Iterator<K> iterator() {
            Node<K,V>[] t;
            ConcurrentHashMap<K,V> m = map;
            int f = (t = m.table) == null ? 0 : t.length;
            return new KeyIterator<K,V>(t, f, 0, f, m);
        }

        /**
         * Adds the specified key to this set view by mapping the key to
         * the default mapped value in the backing map, if defined.
         *
         * @param e key to be added
         * @return {@code true} if this set changed as a result of the call
         * @throws NullPointerException if the specified key is null
         * @throws UnsupportedOperationException if no default mapped value
         * for additions was provided
         */
        public boolean add(K e) {
            V v;
            if ((v = value) == null)
                throw new UnsupportedOperationException();
            return map.putVal(e, v, true) == null;
        }

        /**
         * Adds all of the elements in the specified collection to this set,
         * as if by calling {@link #add} on each one.
         *
         * @param c the elements to be inserted into this set
         * @return {@code true} if this set changed as a result of the call
         * @throws NullPointerException if the collection or any of its
         * elements are {@code null}
         * @throws UnsupportedOperationException if no default mapped value
         * for additions was provided
         */
        public boolean addAll(Collection<? extends K> c) {
            boolean added = false;
            V v;
            if ((v = value) == null)
                throw new UnsupportedOperationException();
            for (K e : c) {
                if (map.putVal(e, v, true) == null)
                    added = true;
            }
            return added;
        }

        public int hashCode() {
            int h = 0;
            for (K e : this)
                h += e.hashCode();
            return h;
        }

        public boolean equals(Object o) {
            Set<?> c;
            return ((o instanceof Set) &&
                    ((c = (Set<?>)o) == this ||
                            (containsAll(c) && c.containsAll(this))));
        }

        public Spliterator<K> spliterator() {
            Node<K,V>[] t;
            ConcurrentHashMap<K,V> m = map;
            long n = m.sumCount();
            int f = (t = m.table) == null ? 0 : t.length;
            return new KeySpliterator<K,V>(t, f, 0, f, n < 0L ? 0L : n);
        }

        public void forEach(Consumer<? super K> action) {
            if (action == null) throw new NullPointerException();
            Node<K,V>[] t;
            if ((t = map.table) != null) {
                Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
                for (Node<K,V> p; (p = it.advance()) != null; )
                    action.accept(p.key);
            }
        }
    }

    /**
     * A view of a ConcurrentHashMap as a {@link Collection} of
     * values, in which additions are disabled. This class cannot be
     * directly instantiated. See {@link #values()}.
     */
    static final class ValuesView<K,V> extends CollectionView<K,V,V>
            implements Collection<V>, java.io.Serializable {
        private static final long serialVersionUID = 2249069246763182397L;
        ValuesView(ConcurrentHashMap<K,V> map) { super(map); }
        public final boolean contains(Object o) {
            return map.containsValue(o);
        }

        public final boolean remove(Object o) {
            if (o != null) {
                for (Iterator<V> it = iterator(); it.hasNext();) {
                    if (o.equals(it.next())) {
                        it.remove();
                        return true;
                    }
                }
            }
            return false;
        }

        public final Iterator<V> iterator() {
            ConcurrentHashMap<K,V> m = map;
            Node<K,V>[] t;
            int f = (t = m.table) == null ? 0 : t.length;
            return new ValueIterator<K,V>(t, f, 0, f, m);
        }

        public final boolean add(V e) {
            throw new UnsupportedOperationException();
        }
        public final boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean removeAll(Collection<?> c) {
            if (c == null) throw new NullPointerException();
            boolean modified = false;
            for (Iterator<V> it = iterator(); it.hasNext();) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        public boolean removeIf(Predicate<? super V> filter) {
            return map.removeValueIf(filter);
        }

        public Spliterator<V> spliterator() {
            Node<K,V>[] t;
            ConcurrentHashMap<K,V> m = map;
            long n = m.sumCount();
            int f = (t = m.table) == null ? 0 : t.length;
            return new ValueSpliterator<K,V>(t, f, 0, f, n < 0L ? 0L : n);
        }

        public void forEach(Consumer<? super V> action) {
            if (action == null) throw new NullPointerException();
            Node<K,V>[] t;
            if ((t = map.table) != null) {
                Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
                for (Node<K,V> p; (p = it.advance()) != null; )
                    action.accept(p.val);
            }
        }
    }

    /**
     * A view of a ConcurrentHashMap as a {@link Set} of (key, value)
     * entries.  This class cannot be directly instantiated. See
     * {@link #entrySet()}.
     */
    static final class EntrySetView<K,V> extends CollectionView<K,V,Map.Entry<K,V>>
            implements Set<Map.Entry<K,V>>, java.io.Serializable {
        private static final long serialVersionUID = 2249069246763182397L;
        EntrySetView(ConcurrentHashMap<K,V> map) { super(map); }

        public boolean contains(Object o) {
            Object k, v, r; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (r = map.get(k)) != null &&
                    (v = e.getValue()) != null &&
                    (v == r || v.equals(r)));
        }

        public boolean remove(Object o) {
            Object k, v; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    map.remove(k, v));
        }

        /**
         * @return an iterator over the entries of the backing map
         */
        public Iterator<Map.Entry<K,V>> iterator() {
            ConcurrentHashMap<K,V> m = map;
            Node<K,V>[] t;
            int f = (t = m.table) == null ? 0 : t.length;
            return new EntryIterator<K,V>(t, f, 0, f, m);
        }

        public boolean add(Entry<K,V> e) {
            return map.putVal(e.getKey(), e.getValue(), false) == null;
        }

        public boolean addAll(Collection<? extends Entry<K,V>> c) {
            boolean added = false;
            for (Entry<K,V> e : c) {
                if (add(e))
                    added = true;
            }
            return added;
        }

        public boolean removeIf(Predicate<? super Entry<K,V>> filter) {
            return map.removeEntryIf(filter);
        }

        public final int hashCode() {
            int h = 0;
            Node<K,V>[] t;
            if ((t = map.table) != null) {
                Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
                for (Node<K,V> p; (p = it.advance()) != null; ) {
                    h += p.hashCode();
                }
            }
            return h;
        }

        public final boolean equals(Object o) {
            Set<?> c;
            return ((o instanceof Set) &&
                    ((c = (Set<?>)o) == this ||
                            (containsAll(c) && c.containsAll(this))));
        }

        public Spliterator<Map.Entry<K,V>> spliterator() {
            Node<K,V>[] t;
            ConcurrentHashMap<K,V> m = map;
            long n = m.sumCount();
            int f = (t = m.table) == null ? 0 : t.length;
            return new EntrySpliterator<K,V>(t, f, 0, f, n < 0L ? 0L : n, m);
        }

        public void forEach(Consumer<? super Map.Entry<K,V>> action) {
            if (action == null) throw new NullPointerException();
            Node<K,V>[] t;
            if ((t = map.table) != null) {
                Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0, t.length);
                for (Node<K,V> p; (p = it.advance()) != null; )
                    action.accept(new MapEntry<K,V>(p.key, p.val, map));
            }
        }

    }

    // -------------------------------------------------------

    /**
     * Base class for bulk tasks. Repeats some fields and code from
     * class Traverser, because we need to subclass CountedCompleter.
     */
    @SuppressWarnings("serial")
    abstract static class BulkTask<K,V,R> extends CountedCompleter<R> {
        Node<K,V>[] tab;        // same as Traverser
        Node<K,V> next;
        TableStack<K,V> stack, spare;
        int index;
        int baseIndex;
        int baseLimit;
        final int baseSize;
        int batch;              // split control

        BulkTask(BulkTask<K,V,?> par, int b, int i, int f, Node<K,V>[] t) {
            super(par);
            this.batch = b;
            this.index = this.baseIndex = i;
            if ((this.tab = t) == null)
                this.baseSize = this.baseLimit = 0;
            else if (par == null)
                this.baseSize = this.baseLimit = t.length;
            else {
                this.baseLimit = f;
                this.baseSize = par.baseSize;
            }
        }

        /**
         * Same as Traverser version.
         */
        final Node<K,V> advance() {
            Node<K,V> e;
            if ((e = next) != null)
                e = e.next;
            for (;;) {
                Node<K,V>[] t; int i, n;
                if (e != null)
                    return next = e;
                if (baseIndex >= baseLimit || (t = tab) == null ||
                        (n = t.length) <= (i = index) || i < 0)
                    return next = null;
                if ((e = tabAt(t, i)) != null && e.hash < 0) {
                    if (e instanceof ForwardingNode) {
                        tab = ((ForwardingNode<K,V>)e).nextTable;
                        e = null;
                        pushState(t, i, n);
                        continue;
                    }
                    else if (e instanceof TreeBin)
                        e = ((TreeBin<K,V>)e).first;
                    else
                        e = null;
                }
                if (stack != null)
                    recoverState(n);
                else if ((index = i + baseSize) >= n)
                    index = ++baseIndex;
            }
        }

        private void pushState(Node<K,V>[] t, int i, int n) {
            TableStack<K,V> s = spare;
            if (s != null)
                spare = s.next;
            else
                s = new TableStack<K,V>();
            s.tab = t;
            s.length = n;
            s.index = i;
            s.next = stack;
            stack = s;
        }

        private void recoverState(int n) {
            TableStack<K,V> s; int len;
            while ((s = stack) != null && (index += (len = s.length)) >= n) {
                n = len;
                index = s.index;
                tab = s.tab;
                s.tab = null;
                TableStack<K,V> next = s.next;
                s.next = spare; // save for reuse
                stack = next;
                spare = s;
            }
            if (s == null && (index += baseSize) >= n)
                index = ++baseIndex;
        }
    }

    /*
     * Task classes. Coded in a regular but ugly format/style to
     * simplify checks that each variant differs in the right way from
     * others. The null screenings exist because compilers cannot tell
     * that we've already null-checked task arguments, so we force
     * simplest hoisted bypass to help avoid convoluted traps.
     */
    @SuppressWarnings("serial")
    static final class ForEachKeyTask<K,V>
            extends BulkTask<K,V,Void> {
        final Consumer<? super K> action;
        ForEachKeyTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Consumer<? super K> action) {
            super(p, b, i, f, t);
            this.action = action;
        }
        public final void compute() {
            final Consumer<? super K> action;
            if ((action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachKeyTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null;)
                    action.accept(p.key);
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ForEachValueTask<K,V>
            extends BulkTask<K,V,Void> {
        final Consumer<? super V> action;
        ForEachValueTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Consumer<? super V> action) {
            super(p, b, i, f, t);
            this.action = action;
        }
        public final void compute() {
            final Consumer<? super V> action;
            if ((action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachValueTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null;)
                    action.accept(p.val);
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ForEachEntryTask<K,V>
            extends BulkTask<K,V,Void> {
        final Consumer<? super Entry<K,V>> action;
        ForEachEntryTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Consumer<? super Entry<K,V>> action) {
            super(p, b, i, f, t);
            this.action = action;
        }
        public final void compute() {
            final Consumer<? super Entry<K,V>> action;
            if ((action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachEntryTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    action.accept(p);
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ForEachMappingTask<K,V>
            extends BulkTask<K,V,Void> {
        final BiConsumer<? super K, ? super V> action;
        ForEachMappingTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 BiConsumer<? super K,? super V> action) {
            super(p, b, i, f, t);
            this.action = action;
        }
        public final void compute() {
            final BiConsumer<? super K, ? super V> action;
            if ((action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachMappingTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    action.accept(p.key, p.val);
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ForEachTransformedKeyTask<K,V,U>
            extends BulkTask<K,V,Void> {
        final Function<? super K, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedKeyTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Function<? super K, ? extends U> transformer, Consumer<? super U> action) {
            super(p, b, i, f, t);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final Function<? super K, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                    (action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachTransformedKeyTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    transformer, action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p.key)) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ForEachTransformedValueTask<K,V,U>
            extends BulkTask<K,V,Void> {
        final Function<? super V, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedValueTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Function<? super V, ? extends U> transformer, Consumer<? super U> action) {
            super(p, b, i, f, t);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final Function<? super V, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                    (action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachTransformedValueTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    transformer, action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p.val)) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ForEachTransformedEntryTask<K,V,U>
            extends BulkTask<K,V,Void> {
        final Function<Map.Entry<K,V>, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedEntryTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Function<Map.Entry<K,V>, ? extends U> transformer, Consumer<? super U> action) {
            super(p, b, i, f, t);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final Function<Map.Entry<K,V>, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                    (action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachTransformedEntryTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    transformer, action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p)) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ForEachTransformedMappingTask<K,V,U>
            extends BulkTask<K,V,Void> {
        final BiFunction<? super K, ? super V, ? extends U> transformer;
        final Consumer<? super U> action;
        ForEachTransformedMappingTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 BiFunction<? super K, ? super V, ? extends U> transformer,
                 Consumer<? super U> action) {
            super(p, b, i, f, t);
            this.transformer = transformer; this.action = action;
        }
        public final void compute() {
            final BiFunction<? super K, ? super V, ? extends U> transformer;
            final Consumer<? super U> action;
            if ((transformer = this.transformer) != null &&
                    (action = this.action) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    new ForEachTransformedMappingTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    transformer, action).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p.key, p.val)) != null)
                        action.accept(u);
                }
                propagateCompletion();
            }
        }
    }

    @SuppressWarnings("serial")
    static final class SearchKeysTask<K,V,U>
            extends BulkTask<K,V,U> {
        final Function<? super K, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchKeysTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Function<? super K, ? extends U> searchFunction,
                 AtomicReference<U> result) {
            super(p, b, i, f, t);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final Function<? super K, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                    (result = this.result) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    if (result.get() != null)
                        return;
                    addToPendingCount(1);
                    new SearchKeysTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    searchFunction, result).fork();
                }
                while (result.get() == null) {
                    U u;
                    Node<K,V> p;
                    if ((p = advance()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(p.key)) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class SearchValuesTask<K,V,U>
            extends BulkTask<K,V,U> {
        final Function<? super V, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchValuesTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Function<? super V, ? extends U> searchFunction,
                 AtomicReference<U> result) {
            super(p, b, i, f, t);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final Function<? super V, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                    (result = this.result) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    if (result.get() != null)
                        return;
                    addToPendingCount(1);
                    new SearchValuesTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    searchFunction, result).fork();
                }
                while (result.get() == null) {
                    U u;
                    Node<K,V> p;
                    if ((p = advance()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(p.val)) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class SearchEntriesTask<K,V,U>
            extends BulkTask<K,V,U> {
        final Function<Entry<K,V>, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchEntriesTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 Function<Entry<K,V>, ? extends U> searchFunction,
                 AtomicReference<U> result) {
            super(p, b, i, f, t);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final Function<Entry<K,V>, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                    (result = this.result) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    if (result.get() != null)
                        return;
                    addToPendingCount(1);
                    new SearchEntriesTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    searchFunction, result).fork();
                }
                while (result.get() == null) {
                    U u;
                    Node<K,V> p;
                    if ((p = advance()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(p)) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        return;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class SearchMappingsTask<K,V,U>
            extends BulkTask<K,V,U> {
        final BiFunction<? super K, ? super V, ? extends U> searchFunction;
        final AtomicReference<U> result;
        SearchMappingsTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 BiFunction<? super K, ? super V, ? extends U> searchFunction,
                 AtomicReference<U> result) {
            super(p, b, i, f, t);
            this.searchFunction = searchFunction; this.result = result;
        }
        public final U getRawResult() { return result.get(); }
        public final void compute() {
            final BiFunction<? super K, ? super V, ? extends U> searchFunction;
            final AtomicReference<U> result;
            if ((searchFunction = this.searchFunction) != null &&
                    (result = this.result) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    if (result.get() != null)
                        return;
                    addToPendingCount(1);
                    new SearchMappingsTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    searchFunction, result).fork();
                }
                while (result.get() == null) {
                    U u;
                    Node<K,V> p;
                    if ((p = advance()) == null) {
                        propagateCompletion();
                        break;
                    }
                    if ((u = searchFunction.apply(p.key, p.val)) != null) {
                        if (result.compareAndSet(null, u))
                            quietlyCompleteRoot();
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ReduceKeysTask<K,V>
            extends BulkTask<K,V,K> {
        final BiFunction<? super K, ? super K, ? extends K> reducer;
        K result;
        ReduceKeysTask<K,V> rights, nextRight;
        ReduceKeysTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 ReduceKeysTask<K,V> nextRight,
                 BiFunction<? super K, ? super K, ? extends K> reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.reducer = reducer;
        }
        public final K getRawResult() { return result; }
        public final void compute() {
            final BiFunction<? super K, ? super K, ? extends K> reducer;
            if ((reducer = this.reducer) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new ReduceKeysTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, reducer)).fork();
                }
                K r = null;
                for (Node<K,V> p; (p = advance()) != null; ) {
                    K u = p.key;
                    r = (r == null) ? u : u == null ? r : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    ReduceKeysTask<K,V>
                            t = (ReduceKeysTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        K tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                    reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ReduceValuesTask<K,V>
            extends BulkTask<K,V,V> {
        final BiFunction<? super V, ? super V, ? extends V> reducer;
        V result;
        ReduceValuesTask<K,V> rights, nextRight;
        ReduceValuesTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 ReduceValuesTask<K,V> nextRight,
                 BiFunction<? super V, ? super V, ? extends V> reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.reducer = reducer;
        }
        public final V getRawResult() { return result; }
        public final void compute() {
            final BiFunction<? super V, ? super V, ? extends V> reducer;
            if ((reducer = this.reducer) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new ReduceValuesTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, reducer)).fork();
                }
                V r = null;
                for (Node<K,V> p; (p = advance()) != null; ) {
                    V v = p.val;
                    r = (r == null) ? v : reducer.apply(r, v);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    ReduceValuesTask<K,V>
                            t = (ReduceValuesTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        V tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                    reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class ReduceEntriesTask<K,V>
            extends BulkTask<K,V,Map.Entry<K,V>> {
        final BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer;
        Map.Entry<K,V> result;
        ReduceEntriesTask<K,V> rights, nextRight;
        ReduceEntriesTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 ReduceEntriesTask<K,V> nextRight,
                 BiFunction<Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.reducer = reducer;
        }
        public final Map.Entry<K,V> getRawResult() { return result; }
        public final void compute() {
            final BiFunction<Map.Entry<K,V>, Map.Entry<K,V>, ? extends Map.Entry<K,V>> reducer;
            if ((reducer = this.reducer) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new ReduceEntriesTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, reducer)).fork();
                }
                Map.Entry<K,V> r = null;
                for (Node<K,V> p; (p = advance()) != null; )
                    r = (r == null) ? p : reducer.apply(r, p);
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    ReduceEntriesTask<K,V>
                            t = (ReduceEntriesTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        Map.Entry<K,V> tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                    reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceKeysTask<K,V,U>
            extends BulkTask<K,V,U> {
        final Function<? super K, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceKeysTask<K,V,U> rights, nextRight;
        MapReduceKeysTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceKeysTask<K,V,U> nextRight,
                 Function<? super K, ? extends U> transformer,
                 BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        public final void compute() {
            final Function<? super K, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceKeysTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, reducer)).fork();
                }
                U r = null;
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p.key)) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceKeysTask<K,V,U>
                            t = (MapReduceKeysTask<K,V,U>)c,
                            s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                    reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceValuesTask<K,V,U>
            extends BulkTask<K,V,U> {
        final Function<? super V, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceValuesTask<K,V,U> rights, nextRight;
        MapReduceValuesTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceValuesTask<K,V,U> nextRight,
                 Function<? super V, ? extends U> transformer,
                 BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        public final void compute() {
            final Function<? super V, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceValuesTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, reducer)).fork();
                }
                U r = null;
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p.val)) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceValuesTask<K,V,U>
                            t = (MapReduceValuesTask<K,V,U>)c,
                            s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                    reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceEntriesTask<K,V,U>
            extends BulkTask<K,V,U> {
        final Function<Map.Entry<K,V>, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceEntriesTask<K,V,U> rights, nextRight;
        MapReduceEntriesTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceEntriesTask<K,V,U> nextRight,
                 Function<Map.Entry<K,V>, ? extends U> transformer,
                 BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        public final void compute() {
            final Function<Map.Entry<K,V>, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceEntriesTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, reducer)).fork();
                }
                U r = null;
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p)) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceEntriesTask<K,V,U>
                            t = (MapReduceEntriesTask<K,V,U>)c,
                            s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                    reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceMappingsTask<K,V,U>
            extends BulkTask<K,V,U> {
        final BiFunction<? super K, ? super V, ? extends U> transformer;
        final BiFunction<? super U, ? super U, ? extends U> reducer;
        U result;
        MapReduceMappingsTask<K,V,U> rights, nextRight;
        MapReduceMappingsTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceMappingsTask<K,V,U> nextRight,
                 BiFunction<? super K, ? super V, ? extends U> transformer,
                 BiFunction<? super U, ? super U, ? extends U> reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.reducer = reducer;
        }
        public final U getRawResult() { return result; }
        public final void compute() {
            final BiFunction<? super K, ? super V, ? extends U> transformer;
            final BiFunction<? super U, ? super U, ? extends U> reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceMappingsTask<K,V,U>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, reducer)).fork();
                }
                U r = null;
                for (Node<K,V> p; (p = advance()) != null; ) {
                    U u;
                    if ((u = transformer.apply(p.key, p.val)) != null)
                        r = (r == null) ? u : reducer.apply(r, u);
                }
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceMappingsTask<K,V,U>
                            t = (MapReduceMappingsTask<K,V,U>)c,
                            s = t.rights;
                    while (s != null) {
                        U tr, sr;
                        if ((sr = s.result) != null)
                            t.result = (((tr = t.result) == null) ? sr :
                                    reducer.apply(tr, sr));
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceKeysToDoubleTask<K,V>
            extends BulkTask<K,V,Double> {
        final ToDoubleFunction<? super K> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceKeysToDoubleTask<K,V> rights, nextRight;
        MapReduceKeysToDoubleTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceKeysToDoubleTask<K,V> nextRight,
                 ToDoubleFunction<? super K> transformer,
                 double basis,
                 DoubleBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        public final void compute() {
            final ToDoubleFunction<? super K> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceKeysToDoubleTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.key));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceKeysToDoubleTask<K,V>
                            t = (MapReduceKeysToDoubleTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceValuesToDoubleTask<K,V>
            extends BulkTask<K,V,Double> {
        final ToDoubleFunction<? super V> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceValuesToDoubleTask<K,V> rights, nextRight;
        MapReduceValuesToDoubleTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceValuesToDoubleTask<K,V> nextRight,
                 ToDoubleFunction<? super V> transformer,
                 double basis,
                 DoubleBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        public final void compute() {
            final ToDoubleFunction<? super V> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceValuesToDoubleTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.val));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceValuesToDoubleTask<K,V>
                            t = (MapReduceValuesToDoubleTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceEntriesToDoubleTask<K,V>
            extends BulkTask<K,V,Double> {
        final ToDoubleFunction<Map.Entry<K,V>> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceEntriesToDoubleTask<K,V> rights, nextRight;
        MapReduceEntriesToDoubleTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceEntriesToDoubleTask<K,V> nextRight,
                 ToDoubleFunction<Map.Entry<K,V>> transformer,
                 double basis,
                 DoubleBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        public final void compute() {
            final ToDoubleFunction<Map.Entry<K,V>> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceEntriesToDoubleTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceEntriesToDoubleTask<K,V>
                            t = (MapReduceEntriesToDoubleTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceMappingsToDoubleTask<K,V>
            extends BulkTask<K,V,Double> {
        final ToDoubleBiFunction<? super K, ? super V> transformer;
        final DoubleBinaryOperator reducer;
        final double basis;
        double result;
        MapReduceMappingsToDoubleTask<K,V> rights, nextRight;
        MapReduceMappingsToDoubleTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceMappingsToDoubleTask<K,V> nextRight,
                 ToDoubleBiFunction<? super K, ? super V> transformer,
                 double basis,
                 DoubleBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Double getRawResult() { return result; }
        public final void compute() {
            final ToDoubleBiFunction<? super K, ? super V> transformer;
            final DoubleBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                double r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceMappingsToDoubleTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.key, p.val));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceMappingsToDoubleTask<K,V>
                            t = (MapReduceMappingsToDoubleTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsDouble(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceKeysToLongTask<K,V>
            extends BulkTask<K,V,Long> {
        final ToLongFunction<? super K> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceKeysToLongTask<K,V> rights, nextRight;
        MapReduceKeysToLongTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceKeysToLongTask<K,V> nextRight,
                 ToLongFunction<? super K> transformer,
                 long basis,
                 LongBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        public final void compute() {
            final ToLongFunction<? super K> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceKeysToLongTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsLong(r, transformer.applyAsLong(p.key));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceKeysToLongTask<K,V>
                            t = (MapReduceKeysToLongTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceValuesToLongTask<K,V>
            extends BulkTask<K,V,Long> {
        final ToLongFunction<? super V> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceValuesToLongTask<K,V> rights, nextRight;
        MapReduceValuesToLongTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceValuesToLongTask<K,V> nextRight,
                 ToLongFunction<? super V> transformer,
                 long basis,
                 LongBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        public final void compute() {
            final ToLongFunction<? super V> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceValuesToLongTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsLong(r, transformer.applyAsLong(p.val));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceValuesToLongTask<K,V>
                            t = (MapReduceValuesToLongTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceEntriesToLongTask<K,V>
            extends BulkTask<K,V,Long> {
        final ToLongFunction<Map.Entry<K,V>> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceEntriesToLongTask<K,V> rights, nextRight;
        MapReduceEntriesToLongTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceEntriesToLongTask<K,V> nextRight,
                 ToLongFunction<Map.Entry<K,V>> transformer,
                 long basis,
                 LongBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        public final void compute() {
            final ToLongFunction<Map.Entry<K,V>> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceEntriesToLongTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsLong(r, transformer.applyAsLong(p));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceEntriesToLongTask<K,V>
                            t = (MapReduceEntriesToLongTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceMappingsToLongTask<K,V>
            extends BulkTask<K,V,Long> {
        final ToLongBiFunction<? super K, ? super V> transformer;
        final LongBinaryOperator reducer;
        final long basis;
        long result;
        MapReduceMappingsToLongTask<K,V> rights, nextRight;
        MapReduceMappingsToLongTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceMappingsToLongTask<K,V> nextRight,
                 ToLongBiFunction<? super K, ? super V> transformer,
                 long basis,
                 LongBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Long getRawResult() { return result; }
        public final void compute() {
            final ToLongBiFunction<? super K, ? super V> transformer;
            final LongBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                long r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceMappingsToLongTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsLong(r, transformer.applyAsLong(p.key, p.val));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceMappingsToLongTask<K,V>
                            t = (MapReduceMappingsToLongTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsLong(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceKeysToIntTask<K,V>
            extends BulkTask<K,V,Integer> {
        final ToIntFunction<? super K> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceKeysToIntTask<K,V> rights, nextRight;
        MapReduceKeysToIntTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceKeysToIntTask<K,V> nextRight,
                 ToIntFunction<? super K> transformer,
                 int basis,
                 IntBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        public final void compute() {
            final ToIntFunction<? super K> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceKeysToIntTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsInt(r, transformer.applyAsInt(p.key));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceKeysToIntTask<K,V>
                            t = (MapReduceKeysToIntTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceValuesToIntTask<K,V>
            extends BulkTask<K,V,Integer> {
        final ToIntFunction<? super V> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceValuesToIntTask<K,V> rights, nextRight;
        MapReduceValuesToIntTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceValuesToIntTask<K,V> nextRight,
                 ToIntFunction<? super V> transformer,
                 int basis,
                 IntBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        public final void compute() {
            final ToIntFunction<? super V> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceValuesToIntTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsInt(r, transformer.applyAsInt(p.val));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceValuesToIntTask<K,V>
                            t = (MapReduceValuesToIntTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceEntriesToIntTask<K,V>
            extends BulkTask<K,V,Integer> {
        final ToIntFunction<Map.Entry<K,V>> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceEntriesToIntTask<K,V> rights, nextRight;
        MapReduceEntriesToIntTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceEntriesToIntTask<K,V> nextRight,
                 ToIntFunction<Map.Entry<K,V>> transformer,
                 int basis,
                 IntBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        public final void compute() {
            final ToIntFunction<Map.Entry<K,V>> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceEntriesToIntTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsInt(r, transformer.applyAsInt(p));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceEntriesToIntTask<K,V>
                            t = (MapReduceEntriesToIntTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class MapReduceMappingsToIntTask<K,V>
            extends BulkTask<K,V,Integer> {
        final ToIntBiFunction<? super K, ? super V> transformer;
        final IntBinaryOperator reducer;
        final int basis;
        int result;
        MapReduceMappingsToIntTask<K,V> rights, nextRight;
        MapReduceMappingsToIntTask
                (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
                 MapReduceMappingsToIntTask<K,V> nextRight,
                 ToIntBiFunction<? super K, ? super V> transformer,
                 int basis,
                 IntBinaryOperator reducer) {
            super(p, b, i, f, t); this.nextRight = nextRight;
            this.transformer = transformer;
            this.basis = basis; this.reducer = reducer;
        }
        public final Integer getRawResult() { return result; }
        public final void compute() {
            final ToIntBiFunction<? super K, ? super V> transformer;
            final IntBinaryOperator reducer;
            if ((transformer = this.transformer) != null &&
                    (reducer = this.reducer) != null) {
                int r = this.basis;
                for (int i = baseIndex, f, h; batch > 0 &&
                        (h = ((f = baseLimit) + i) >>> 1) > i;) {
                    addToPendingCount(1);
                    (rights = new MapReduceMappingsToIntTask<K,V>
                            (this, batch >>>= 1, baseLimit = h, f, tab,
                                    rights, transformer, r, reducer)).fork();
                }
                for (Node<K,V> p; (p = advance()) != null; )
                    r = reducer.applyAsInt(r, transformer.applyAsInt(p.key, p.val));
                result = r;
                CountedCompleter<?> c;
                for (c = firstComplete(); c != null; c = c.nextComplete()) {
                    @SuppressWarnings("unchecked")
                    MapReduceMappingsToIntTask<K,V>
                            t = (MapReduceMappingsToIntTask<K,V>)c,
                            s = t.rights;
                    while (s != null) {
                        t.result = reducer.applyAsInt(t.result, s.result);
                        s = t.rights = s.nextRight;
                    }
                }
            }
        }
    }

    // Unsafe mechanics
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long SIZECTL
                                  = U.objectFieldOffset(ConcurrentHashMap.class, "sizeCtl");
    private static final long TRANSFERINDEX
                                  = U.objectFieldOffset(ConcurrentHashMap.class, "transferIndex");
    private static final long BASECOUNT
                                  = U.objectFieldOffset(ConcurrentHashMap.class, "baseCount");
    private static final long CELLSBUSY
                                  = U.objectFieldOffset(ConcurrentHashMap.class, "cellsBusy");
    private static final long CELLVALUE
                                  = U.objectFieldOffset(CounterCell.class, "value");
    private static final int ABASE = U.arrayBaseOffset(Node[].class);
    private static final int ASHIFT;

    static {
        int scale = U.arrayIndexScale(Node[].class);
        if ((scale & (scale - 1)) != 0)
            throw new ExceptionInInitializerError("array index scale not a power of two");
        ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);

        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;

        // Eager class load observed to help JIT during startup
        ensureLoaded = ReservationNode.class;
    }
}
