package java.util;  // Java工具包

public class LinkedHashSet<E>
        extends HashSet<E>  // 继承HashSet
        implements Set<E>, Cloneable, java.io.Serializable {  // 实现Set、Cloneable、Serializable接口

    @java.io.Serial
    private static final long serialVersionUID = -2851667679971038690L;  // 序列化版本ID

    /**

     * 构造一个具有指定初始容量和负载因子的新的空链式哈希集。
     *
     * @param      initialCapacity 链式哈希集的初始容量
     * @param      loadFactor      链式哈希集的负载因子
     * @throws     IllegalArgumentException  如果初始容量小于零，或负载因子为非正数
     */
    public LinkedHashSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);  // 调用HashSet的特殊构造器，第三个参数true表示创建LinkedHashMap
    }

    /**
     * 构造一个具有指定初始容量和默认负载因子(0.75)的新的空链式哈希集。
     *
     * @param   initialCapacity   LinkedHashSet的初始容量
     * @throws  IllegalArgumentException 如果初始容量小于零
     */
    public LinkedHashSet(int initialCapacity) {
        super(initialCapacity, .75f, true);  // 调用HashSet的特殊构造器，默认负载因子0.75
    }

    /**
     * 构造一个具有默认初始容量(16)和默认负载因子(0.75)的新的空链式哈希集。
     */
    public LinkedHashSet() {
        super(16, .75f, true);  // 调用HashSet的特殊构造器，默认容量16，负载因子0.75
    }

    /**
     * 构造一个包含指定集合中相同元素的新链式哈希集。
     * 链式哈希集以足以容纳指定集合中元素的初始容量和默认负载因子(0.75)创建。
     *
     * @param c  要将其元素放入此集合的集合
     * @throws NullPointerException 如果指定的集合为null
     */
    public LinkedHashSet(Collection<? extends E> c) {
        // 计算初始容量：max(2*c.size(), 11)，确保有足够空间
        super(Math.max(2*c.size(), 11), .75f, true);
        addAll(c);  // 添加所有元素
    }

    @Override
    public Spliterator<E> spliterator() {
        // 返回一个Spliterator，支持DISTINCT（无重复）和ORDERED（有序）特性
        return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
    }
}