# Java8集合框架目录

## 一、集合框架总览

最近在面试，工作了四年了，发现有些底层的数据结构与原理只知道个大概，以前也反复看过很多次，但是一放下就很容易忘记，所以打算系统的读一下。

![](pngs\Collection.png)

![](pngs\Map.png)

## 二、Collection接口体系

### 2.1 List接口
#### **ArrayList**

![](pngs\arraylist.png)

```java
package java.util;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import jdk.internal.access.SharedSecrets;
import jdk.internal.util.ArraysSupport;


/**
 * 首先关于我们的ArrayList一般在定义的时候呢通常会指定类泛型，后续我们操作的时候都是这个类型（当然不考虑泛型擦除问题）
 * 最近被问到了当我们操作一个集合的时候，集合内部存储的实例类型是什么，其实就是我们定义的这个类型，当然
 * 如果不传的话默认就是Object
 */
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
    @java.io.Serial
    private static final long serialVersionUID = 8683452581122892189L;

    /**
     * 默认容量 - ArrayList的默认初始容量
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * 空数组 - 用于指定容量为0的ArrayList
     */
    private static final Object[] EMPTY_ELEMENTDATA = {};

    /**
     * 默认空数组 - 与EMPTY_ELEMENTDATA区分扩容行为
     */
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    /**
     * 存储数组 - 实际存储元素的数组缓冲区
     */
    transient Object[] elementData; // non-private to simplify nested class access

    /**
     * 元素数量 - 记录ArrayList中实际包含的元素数量
     */
    private int size;

    /**
     * 初始化容量的有参构造器 - 
     * @Param initialCapacity 初始化容量
     */
    public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
            this.elementData = new Object[initialCapacity]; // 初始化存储数组，大小为initialCapacity
        } else if (initialCapacity == 0) {
            this.elementData = EMPTY_ELEMENTDATA;// 否则初始化为空数组
        } else {
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        }
    }

    /**
     * 无参构造函数 - 仅仅初始化空数组，不在构造函数中立即分配内存
     */
    public ArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    /**
     * 集合构造函数 - 
     */
    public ArrayList(Collection<? extends E> c) {
        Object[] a = c.toArray(); // 获取入参集合 -> 数组
        if ((size = a.length) != 0) { // 当前集合元素数量复制并且判断不为空
            if (c.getClass() == ArrayList.class) {// 如果传入的是也是ArrayList 直接元素赋值
                elementData = a;
            } else {// 如果不是ArrayList 通过Arrays.copyOf进行赋值操作
                elementData = Arrays.copyOf(a, size, Object[].class);
            }
            // 此时当前List已初始化元素与元素大小两个变量
        } else {
            // replace with empty array.
            elementData = EMPTY_ELEMENTDATA; // 置空
        }
    }

    /**
     * 裁剪ArrayList的容量，使其刚好等于当前元素数量，释放多余的内存空间
     * 
     * 这里有被问到过面试题，大家都知道ArrayList有扩容，那么有没有缩容呢
     * 答案是没有自动从容，只有手动释放
     */
    public void trimToSize() {
        modCount++;// AbstractList - 该列表结构性 修改的次数。结构性修改是指改变列表大小
        if (size < elementData.length) {// 当前数组中元素实际个数小于数组容量
            elementData = (size == 0)
              ? EMPTY_ELEMENTDATA // 空数组 - 直接置空
              : Arrays.copyOf(elementData, size); // 不是空数组 -- 假如size == 8 那么就创建一个等于8的新数组赋值
        }
    }

    /**
     * 如有必要，增加该 ArrayList 实例的容量，以确保其至少能容纳最小容量参数中指定的元素数量。
	 * 参数：minCapacity ——期望的最小容量
     */
    public void ensureCapacity(int minCapacity) {
        if (minCapacity > elementData.length // 期望容量大于当前容量需要扩容
            && !(elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA // 空数组无需扩容并且
                 && minCapacity <= DEFAULT_CAPACITY)) { // 期望容量小于10无需扩容
            modCount++; // AbstractList - 该列表结构性 修改的次数。结构性修改是指改变列表大小
            grow(minCapacity); // 执行扩容操作
        }
    }

    /**
     * 扩容
     * @param 最小容量
     */
    private Object[] grow(int minCapacity) {
        int oldCapacity = elementData.length; // 获取原始数组容量大小
        if (oldCapacity > 0 || elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {// 原始容量大于0或者数组不为空
            int newCapacity = ArraysSupport.newLength(oldCapacity,
                    minCapacity - oldCapacity, /* minimum growth */
                    oldCapacity >> 1           /* preferred growth */);// 获取新数组容量
            return elementData = Arrays.copyOf(elementData, newCapacity);// 拷贝旧数组到新数组并返回
        } else {
            return elementData = new Object[Math.max(DEFAULT_CAPACITY, minCapacity)];// 第一次扩容
        }
    }

    private Object[] grow() {
        return grow(size + 1);
    }

    /**
     * 返回元素个数
     */
    public int size() {
        return size;
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 判读是否包含某个元素
     */
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * 获取元素索引
     */
    public int indexOf(Object o) {
        return indexOfRange(o, 0, size);
    }

    int indexOfRange(Object o, int start, int end) {
        Object[] es = elementData;// 缓存到局部变量，避免多次访问堆上的成员变量，提高性能
        if (o == null) {// 查找空对象
            for (int i = start; i < end; i++) {
                if (es[i] == null) {
                    return i; // 返回索引
                }
            }
        } else { 
            for (int i = start; i < end; i++) {
                if (o.equals(es[i])) { // 查找非空对象
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 元素最后一次出现的索引
     */
    public int lastIndexOf(Object o) {
        return lastIndexOfRange(o, 0, size);
    }

    int lastIndexOfRange(Object o, int start, int end) {
        Object[] es = elementData; // 缓存到局部变量，避免多次访问堆上的成员变量，提高性能
        if (o == null) {
            for (int i = end - 1; i >= start; i--) {// 从后往前查
                if (es[i] == null) {// 查找null元素
                    return i;
                }
            }
        } else {
            for (int i = end - 1; i >= start; i--) {// 从后往前查
                if (o.equals(es[i])) {// 查找目标元素
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 浅拷贝，当数组元素是可变的时候会原对象有影响
     */
    public Object clone() {
        try {
            ArrayList<?> v = (ArrayList<?>) super.clone();
            v.elementData = Arrays.copyOf(elementData, size);
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    /**
     * 转数组
     */
    public Object[] toArray() {
        return Arrays.copyOf(elementData, size);
    }

    /**
     * 根据下标获取元素
     */
    public E get(int index) {
        Objects.checkIndex(index, size);// 检查元素是否在数组有效元素范围内
        return elementData(index);// 获取目标元素
    }

    /**
     * 替换指定位置的元素
     */
    public E set(int index, E element) {
        Objects.checkIndex(index, size);// 检查元素是否在数组有效元素范围内
        E oldValue = elementData(index);// 获取旧值
        elementData[index] = element;// 新值赋值
        return oldValue;// 返回旧值
    }

    /**
     * 检查容量与扩容
     */
    private void add(E e, Object[] elementData, int s) {
        if (s == elementData.length)//s-当前元素数量，如果等于当前数组容量
            elementData = grow();// 执行扩容操作
        elementData[s] = e;// 添加新元素
        size = s + 1;// 数组元素+1
    }

    /**
     * 添加元素并且容量不足会扩容
     */
    public boolean add(E e) {
        modCount++;// AbstractList - 该列表结构性 修改的次数。结构性修改是指改变列表大小
        add(e, elementData, size);// 添加元素
        return true;
    }

    /**
     * 添加元素到指定索引位置
     */
    public void add(int index, E element) {
        rangeCheckForAdd(index);// 索引的合法性校验
        modCount++;// AbstractList - 该列表结构性 修改的次数。结构性修改是指改变列表大小
        final int s;// 当前元素数量
        Object[] elementData;// 当前存储元素数组
        if ((s = size) == (elementData = this.elementData).length)// 元素占满容量
            elementData = grow();// 扩容
        System.arraycopy(elementData, index,
                         elementData, index + 1,
                         s - index);// 插入下标位置以后的所有元素后移一个单位
        elementData[index] = element;// 插入新元素
        size = s + 1;// 元素大小 +1
    }

    /**
     * 删除指定索引的元素
     */
    public E remove(int index) {
        Objects.checkIndex(index, size);// 检查元素是否在数组有效元素范围内
        final Object[] es = elementData;// 缓存到局部变量，避免多次访问堆上的成员变量，提高性能

        @SuppressWarnings("unchecked") E oldValue = (E) es[index];
        fastRemove(es, index);

        return oldValue;
    }

    /**
     * 将指定对象与该列表进行比较以实现相等。
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof List)) {
            return false;
        }

        final int expectedModCount = modCount;
        // ArrayList can be subclassed and given arbitrary behavior, but we can
        // still deal with the common case where o is ArrayList precisely
        boolean equal = (o.getClass() == ArrayList.class)
            ? equalsArrayList((ArrayList<?>) o)
            : equalsRange((List<?>) o, 0, size);

        checkForComodification(expectedModCount);
        return equal;
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If the list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * {@code i} such that
     * {@code Objects.equals(o, get(i))}
     * (if such an element exists).  Returns {@code true} if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if this list contained the specified element
     */
    public boolean remove(Object o) {
        final Object[] es = elementData;
        final int size = this.size;
        int i = 0;
        found: {
            if (o == null) {
                for (; i < size; i++)
                    if (es[i] == null)
                        break found;
            } else {
                for (; i < size; i++)
                    if (o.equals(es[i]))
                        break found;
            }
            return false;
        }
        fastRemove(es, i);
        return true;
    }

    /**
     * 根据索引删除元素
     */
    private void fastRemove(Object[] es, int i) {
        modCount++;
        final int newSize;
        if ((newSize = size - 1) > i)// 检查删除元素是不是最后一个元素
            System.arraycopy(es, i + 1, es, i, newSize - i);// 不是最后一个元素把目标位置后面的所有元素通过拷贝前移
        es[size = newSize] = null;// 方便垃圾回收
    }

    /**
     * 清除所有的元素
     */
    public void clear() {
        modCount++;
        final Object[] es = elementData;// 获取元素数组
        for (int to = size, i = size = 0; i < to; i++)// 循环清除
            es[i] = null;
    }

    /**
     * 添加集合元素（容量不足会扩容）
     */
    public boolean addAll(Collection<? extends E> c) {
        Object[] a = c.toArray();
        modCount++;
        int numNew = a.length;
        if (numNew == 0)
            return false;
        Object[] elementData;
        final int s;
        if (numNew > (elementData = this.elementData).length - (s = size))// 新元素大于剩余容量
            elementData = grow(s + numNew);// 数组扩容
        System.arraycopy(a, 0, elementData, s, numNew);// 添加数组复制到扩容后或者老数组
        size = s + numNew;// 数组元素重置
        return true;
    }

    /**
     * 在指定索引位置添加集合（涉及到扩容或元素后移操作）
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        rangeCheckForAdd(index);

        Object[] a = c.toArray();
        modCount++;
        int numNew = a.length;
        if (numNew == 0)
            return false;
        Object[] elementData;
        final int s;
        if (numNew > (elementData = this.elementData).length - (s = size))// 容量不足
            elementData = grow(s + numNew);// 扩容

        int numMoved = s - index;// 后移个数
        if (numMoved > 0)// 需要后移元素
            System.arraycopy(elementData, index,
                             elementData, index + numNew,
                             numMoved);// 拷贝实现插入位置后的元素整体后移
        System.arraycopy(a, 0, elementData, index, numNew);// 直接拷贝新元素
        size = s + numNew;// 重置元素大小
        return true;
    }     
}

```



#### **LinkedList**

![](pngs\linkedlist.png)

```java
package java.util;

import java.util.function.Consumer;

public class LinkedList<E>
        extends AbstractSequentialList<E>
        implements List<E>, Deque<E>, Cloneable, java.io.Serializable
{
    transient int size = 0;

    /**
     * 指向第一个节点
     */
    transient Node<E> first;

    /**
     * 指向最后一个节点
     */
    transient Node<E> last;

    /**
     * 无参构造甘薯
     */
    public LinkedList() {
    }

    /**
     * 有参构造函数，将集合中的元素添加到链表中
     */
    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * 将元素添加为链表的第一个元素
     */
    private void linkFirst(E e) {
        final Node<E> f = first;// 第一个节点指向f
        final Node<E> newNode = new Node<>(null, e, f);// 前置节点为空，后置节点为第一个节点
        first = newNode;// 重置first节点为新添加的头节点
        if (f == null)// 链表为空
            last = newNode;// 链表为空时，最后一个节点也指向新添加的头节点
        else
            f.prev = newNode;// 第一个节点的前置节点指向新添加的头节点
        size++;// 链表个数+1
        modCount++;// 修改次数+1
    }

    /**
     * 将元素添加为链表的最后一个元素
     */
    void linkLast(E e) {
        final Node<E> l = last;// 最后节点指向l
        final Node<E> newNode = new Node<>(l, e, null);// 最后节点的前置节点为l
        last = newNode;// last指向最后的节点
        if (l == null)// 如果链表为空
            first = newNode;// 链表为空时，第一个节点也指向新添加的头节点
        else
            l.next = newNode;// 最后节点的后置节点指向新添加的头节点
        size++;// 链表个数+1
        modCount++;// 修改次数+1
    }

    /**
     * 在非空节点之前添加节点
     */
    void linkBefore(E e, Node<E> succ) {
        // assert succ != null;
        final Node<E> pred = succ.prev;// 获取这个非空节点的前置节点
        final Node<E> newNode = new Node<>(pred, e, succ);// 创建新节点
        succ.prev = newNode;// 非空节点的前置节点为新元素
        if (pred == null)// 如果前置节点为空，说明当前节点为第一个节点
            first = newNode;// 链表为空时，第一个节点也指向新添加的头节点
        else
            pred.next = newNode;// 前置节点的后置节点指向新添加的头节点
        size++;// 链表个数+1
        modCount++;// 修改次数+1
    }

    /**
     * 删除头节点
     */
    private E unlinkFirst(Node<E> f) {
        // assert f == first && f != null;
        final E element = f.item;// 暂存f节点的数据
        final Node<E> next = f.next;// 获取f节点的下一个节点
        f.item = null;
        f.next = null; // help GC
        first = next;// 下一个节点作为头节点
        if (next == null)// 如果下一个节点为空，说明当前链表只有一个节点
            last = null;// 如果下一个节点为空，说明当前链表只有一个节点，最后一个节点也指向null
        else
            next.prev = null;// 下一个节点的前置节点指向null
        size--;// 链表个数-1
        modCount++;// 修改次数+1
        return element;
    }

    /**
     * 删除尾节点
     */
    private E unlinkLast(Node<E> l) {
        // assert l == last && l != null;
        final E element = l.item;// 暂存尾节点数据
        final Node<E> prev = l.prev;// 获取尾结点的前置节点
        l.item = null;
        l.prev = null; // help GC
        last = prev;// 重置last为当前删除节点的前置节点
        if (prev == null)// 如果前置节点为空，说明当前节点为最后一个节点
            first = null;// 如果前置节点为空，说明当前节点为最后一个节点，链表为空
        else
            prev.next = null;// 前置节点的后置节点指向null
        size--;// 链表个数-1
        modCount++;// 修改次数+1
        return element;
    }

    /**
     * 删除某个非空节点
     */
    E unlink(Node<E> x) {
        // assert x != null;
        final E element = x.item;// 暂存当前节点的数据
        final Node<E> next = x.next;// 获取当前节点的后置节点
        final Node<E> prev = x.prev;// 获取当前节点的前置节点

        if (prev == null) {// 如果前置节点为空，说明当前节点为第一个节点
            first = next;// 下一个节点作为头节点
        } else {
            prev.next = next;// 当前节点的前置节点 的后置节点 指向当前节点的后置节点
            x.prev = null;// 当前节点的前置节点指向null
        }

        if (next == null) {// 如果后置节点为空，说明当前节点为最后一个节点
            last = prev;// 重置last为当前删除节点的前置节点
        } else {
            next.prev = prev;// 当前节点的后置节点 的前置节点 指向当前节点的前置节点
            x.next = null;// 当前节点的后置节点指向null
        }

        x.item = null;
        size--;// 链表个数-1
        modCount++;// 修改次数+1
        return element;
    }

    /**
     * 获取头节点
     */
    public E getFirst() {
        final Node<E> f = first;// 获取头节点
        if (f == null)
            throw new NoSuchElementException();
        return f.item;
    }

    /**
     * 获取尾节点
     */
    public E getLast() {
        final Node<E> l = last;// 获取尾节点
        if (l == null)
            throw new NoSuchElementException();
        return l.item;
    }

    /**
     * 删除头节点
     */
    public E removeFirst() {
        final Node<E> f = first;// 获取头节点
        if (f == null)
            throw new NoSuchElementException();
        return unlinkFirst(f);// 删除头节点
    }

    /**
     * 删除尾节点
     */
    public E removeLast() {
        final Node<E> l = last;// 获取尾节点
        if (l == null)
            throw new NoSuchElementException();
        return unlinkLast(l);// 删除尾节点
    }

    /**
     * 在头节点前添加元素
     */
    public void addFirst(E e) {
        linkFirst(e);
    }

    /**
     * 在尾节点后添加元素
     */
    public void addLast(E e) {
        linkLast(e);
    }

    /**
     * 判断链表是否包含某个元素
     */
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * 返回链表大小
     */
    public int size() {
        return size;
    }

    /**
     * 添加元素到链表尾部
     */
    public boolean add(E e) {
        linkLast(e);
        return true;
    }

    /**
     * 删除某个元素
     */
    public boolean remove(Object o) {
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {// 遍历链表
                if (x.item == null) {// 如果当前节点的数据为空
                    unlink(x);// 删除当前节点
                    return true;
                }
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {// 遍历链表
                if (o.equals(x.item)) {// 如果当前节点的数据与指定元素相等
                    unlink(x);// 删除当前节点
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 添加集合元素到尾部
     */
    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }

    /**
     * 添加集合元素到链表指定位置
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        checkPositionIndex(index);// 检查索引是否越界

        Object[] a = c.toArray();// 将集合转换为数组
        int numNew = a.length;// 新集合的元素个数
        if (numNew == 0)
            return false;

        Node<E> pred, succ;// pred: 前置节点, succ: 后置节点
        if (index == size) {// 如果索引等于链表的大小，说明是在链表的末尾添加元素
            succ = null;// 后继结点指向空
            pred = last;// 前置节点指向最后一个节点
        } else {
            succ = node(index);// 获取索引位置的节点（原节点：被占用的节点）
            pred = succ.prev;// 获取索引位置节点的前置节点（原节点：被占用的节点）
        }

        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E) o;
            Node<E> newNode = new Node<>(pred, e, null);
            if (pred == null)// 如果前置节点为空，说明是在链表的头部添加元素
                first = newNode;// 头节点指向新节点
            else
                pred.next = newNode;// 前置节点的后继节点指向新节点
            pred = newNode;// 前置节点指向新节点
        }

        if (succ == null) {// 如果之前原节点获取到后置节点为空，说明是在链表的末尾添加元素
            last = pred;// 最后一个节点指向新节点
        } else {
            pred.next = succ;// 新节点指向被占用的节点
            succ.prev = pred;// 被占用的节点的前置节点指向新节点
        }

        size += numNew;// 链表的大小增加新集合的元素个数
        modCount++;// 修改次数增加
        return true;
    }

    /**
     * Removes all of the elements from this list.
     * The list will be empty after this call returns.
     */
    public void clear() {
        // Clearing all of the links between nodes is "unnecessary", but:
        // - helps a generational GC if the discarded nodes inhabit
        //   more than one generation
        // - is sure to free memory even if there is a reachable Iterator
        for (Node<E> x = first; x != null; ) {
            Node<E> next = x.next;
            x.item = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        first = last = null;
        size = 0;
        modCount++;
    }


    // Positional Access Operations

    /**
     * 获取指定下标元素
     */
    public E get(int index) {
        checkElementIndex(index);// 检查索引是否越界
        return node(index).item;
    }

    /**
     * 更新指定下标元素数据
     */
    public E set(int index, E element) {
        checkElementIndex(index);// 检查索引是否越界
        Node<E> x = node(index);// 获取索引位置的节点（原节点：被占）的节点用
        E oldVal = x.item;// 获取索引位置节点的数据（原数据：被占）
        x.item = element;// 更新索引位置节点的数据（新数据：占）
        return oldVal;// 返回原数据（被占）
    }

    /**
     * 按照指定下标添加元素
     */
    public void add(int index, E element) {
        checkPositionIndex(index);// 检查索引是否越界

        if (index == size)// 如果索引等于链表的大小，说明是在链表的末尾添加元素
            linkLast(element);// 在链表的末尾添加元素
        else
            linkBefore(element, node(index));// 在索引位置的节点之前添加元素
    }

    /**
     * 删除指定下标元素
     */
    public E remove(int index) {
        checkElementIndex(index);// 检查索引是否越界
        return unlink(node(index));// 删除索引位置的节点
    }

    /**
     * Tells if the argument is the index of an existing element.
     */
    private boolean isElementIndex(int index) {
        return index >= 0 && index < size;
    }

    /**
     * Tells if the argument is the index of a valid position for an
     * iterator or an add operation.
     */
    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= size;
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    private void checkElementIndex(int index) {
        if (!isElementIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private void checkPositionIndex(int index) {
        if (!isPositionIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * 返回指定元素索引处的（非空）节点
     */
    Node<E> node(int index) {
        // assert isElementIndex(index);

        if (index < (size >> 1)) {
            Node<E> x = first;
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        } else {
            Node<E> x = last;
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }

    // Search Operations

    /**
     * 返回指定元素首次出现的索引；如果此列表不包含该元素，则返回 -1。
     */
    public int indexOf(Object o) {
        int index = 0;
        if (o == null) {// 如果元素为null
            for (Node<E> x = first; x != null; x = x.next) {// 遍历链表
                if (x.item == null)// 如果节点元素为null
                    return index;// 如果节点元素为null，返回当前索引
                index++;// 索引加1
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {// 遍历链表
                if (o.equals(x.item))// 如果元素相等
                    return index;// 返回当前索引
                index++;// 索引加1
            }
        }
        return -1;
    }

    /**
     * 返回指定元素最后一次出现的索引；如果此列表不包含该元素，则返回 -1。（从后往前遍历）
     */
    public int lastIndexOf(Object o) {
        int index = size;
        if (o == null) {// 如果元素为null
            for (Node<E> x = last; x != null; x = x.prev) {// 从后往前遍历链表
                index--;// 索引减1
                if (x.item == null)// 如果节点元素为null
                    return index;// 如果节点元素为null，返回当前索引
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {// 从后往前遍历链表
                index--;// 索引减1
                if (o.equals(x.item))// 如果元素相等
                    return index;// 返回当前索引
            }
        }
        return -1;
    }

    // Queue operations.

    /**
     * 获取第一个节点元素
     */
    public E peek() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
    }

    /**
     * 获取第一个元素
     */
    public E element() {
        return getFirst();
    }

    /**
     * 获取并移除第一个元素
     */
    public E poll() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * 删除第一个元素
     */
    public E remove() {
        return removeFirst();
    }

    /**
     * 将指定元素添加到链表尾部
     */
    public boolean offer(E e) {
        return add(e);
    }

    // Deque operations
    /**
     * 将指定元素添加到链表最前
     */
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    /**
     * 将指定元素添加到链表尾部
     */
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /**
     * 获取第一个元素
     */
    public E peekFirst() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
    }

    /**
     * 获取最后一个元素
     */
    public E peekLast() {
        final Node<E> l = last;
        return (l == null) ? null : l.item;
    }

    /**
     * 获取并移除第一个元素
     */
    public E pollFirst() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * 获取并移除最后一个元素
     */
    public E pollLast() {
        final Node<E> l = last;
        return (l == null) ? null : unlinkLast(l);
    }

    /**
     * 添加元素到列表头部
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * 产出以第一元素并返回
     */
    public E pop() {
        return removeFirst();
    }

    /**
     * 从前向后便利删除第一个出现的元素
     */
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    /**
     * 从后向前便利删除第一个出现的元素
     */
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }


    private static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }

    /**
     * 转为数组
     */
    public Object[] toArray() {
        Object[] result = new Object[size];
        int i = 0;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;
        return result;
    }

}

```

#### **Vector**

![](pngs\vector.png)

```java
package java.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import jdk.internal.util.ArraysSupport;

public class Vector<E>
        extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
    /**
     * 元素存储数组
     */
    @SuppressWarnings("serial") // Conditionally serializable
    protected Object[] elementData;

    /**
     * 实际存储元素数量
     */
    protected int elementCount;

    /**
     * 容量增量 小于等于0时，则需要增长时容量会翻倍
     */
    protected int capacityIncrement;


    /**
     * 初始容量和容量增量构造器
     */
    public Vector(int initialCapacity, int capacityIncrement) {
        super();
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "+
                    initialCapacity);
        this.elementData = new Object[initialCapacity];
        this.capacityIncrement = capacityIncrement;
    }

    /**
     * 初始容量和容量增量为0构造器
     */
    public Vector(int initialCapacity) {
        this(initialCapacity, 0);
    }

    /**
     * 初始容量和容量增量为0构造器
     */
    public Vector() {
        this(10);
    }

    /**
     * 初始化元素构造器
     */
    public Vector(Collection<? extends E> c) {
        Object[] a = c.toArray();
        elementCount = a.length;// 元素属性个数为入参集合大小
        if (c.getClass() == ArrayList.class) {// 如果是Arraylist
            elementData = a;// 直接指向Arraylist中的数组
        } else {
            elementData = Arrays.copyOf(a, elementCount, Object[].class);// 拷贝一份
        }
    }

    /**
     * 赋值当前Vector数组到目标数组（会覆盖目标数组）
     */
    public synchronized void copyInto(Object[] anArray) {
        System.arraycopy(elementData, 0, anArray, 0, elementCount);
    }

    /**
     * 缩容
     */
    public synchronized void trimToSize() {
        modCount++;
        int oldCapacity = elementData.length;// 当前容量大小
        if (elementCount < oldCapacity) {// 如果元素个数小于当前容量大小
            elementData = Arrays.copyOf(elementData, elementCount);// 拷贝一个新数组容量大小为元素个数
        }
    }

    /**
     * 扩容
     */
    public synchronized void ensureCapacity(int minCapacity) {
        if (minCapacity > 0) {
            modCount++;
            if (minCapacity > elementData.length)// 期望容量大于当前容量大小
                grow(minCapacity);// 扩容
        }
    }

    /**
     * 具体扩容算法
     */
    private Object[] grow(int minCapacity) {
        int oldCapacity = elementData.length;// 旧容量
        // 如果增量小于等于0则增量翻倍，否则newCapacity = oldCapacity + capacityIncrement
        int newCapacity = ArraysSupport.newLength(oldCapacity,
                minCapacity - oldCapacity, /* minimum growth */
                capacityIncrement > 0 ? capacityIncrement : oldCapacity
                /* preferred growth */);
        return elementData = Arrays.copyOf(elementData, newCapacity);// 复制到新数组并且指定容量且重新指向
    }

    private Object[] grow() {
        return grow(elementCount + 1);
    }

    /**
     * 设置Vector的大小（元素个数）。如果新大小大于当前大小，会添加 null 元素；如果新大小小于当前大小，会丢弃多余元素
     */
    public synchronized void setSize(int newSize) {
        modCount++;
        if (newSize > elementData.length)// 入参大于当前容量
            grow(newSize);//扩容操作
        final Object[] es = elementData;
        for (int to = elementCount, i = newSize; i < to; i++)// 丢弃元素
            es[i] = null;
        elementCount = newSize;// 重置元素大小
    }

    /**
     * 获取容量
     */
    public synchronized int capacity() {
        return elementData.length;
    }

    /**
     * 获取元素个数
     */
    public synchronized int size() {
        return elementCount;
    }

    /**
     * 判断是否为空
     */
    public synchronized boolean isEmpty() {
        return elementCount == 0;
    }

    /**
     * 判断是否包含某个元素
     */
    public boolean contains(Object o) {
        return indexOf(o, 0) >= 0;
    }

    /**
     * 返回数组中首次出现的索引
     */
    public int indexOf(Object o) {
        return indexOf(o, 0);
    }

    /**
     * 返回从指定位置开始首次出现的目标元素索引
     */
    public synchronized int indexOf(Object o, int index) {
        if (o == null) {// 查找空元素
            for (int i = index ; i < elementCount ; i++)
                if (elementData[i]==null)// 如果当前元素为空
                    return i;// 返回索引
        } else {
            for (int i = index ; i < elementCount ; i++)
                if (o.equals(elementData[i]))// 如果当前元素等于目标元素
                    return i;// 返回索引
        }
        return -1;
    }

    /**
     * 从后向前遍历，返回第一次出现的目标元素索引
     */
    public synchronized int lastIndexOf(Object o) {
        return lastIndexOf(o, elementCount-1);
    }

    /**
     * 从指定索引由后向前遍历，返回第一次出现的目标元素索引
     */
    public synchronized int lastIndexOf(Object o, int index) {
        if (index >= elementCount)
            throw new IndexOutOfBoundsException(index + " >= "+ elementCount);

        if (o == null) {
            for (int i = index; i >= 0; i--)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = index; i >= 0; i--)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 根据索引获取目标元素
     */
    public synchronized E elementAt(int index) {
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + elementCount);
        }

        return elementData(index);// 直接从数组中获取目标元素
    }

    /**
     * 获取第一个元素
     */
    public synchronized E firstElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData(0);
    }

    /**
     * 获取最后一个元素
     */
    public synchronized E lastElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData(elementCount - 1);
    }

    /**
     * 设置指定索引的目标元素
     */
    public synchronized void setElementAt(E obj, int index) {
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " +
                    elementCount);
        }
        elementData[index] = obj;
    }

    /**
     * 删除目标索引的元素
     */
    public synchronized void removeElementAt(int index) {
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " +
                    elementCount);
        }
        else if (index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        int j = elementCount - index - 1;// 计算需要移动的元素数量
        if (j > 0) {// 如果被删除元素后面还有元素
            System.arraycopy(elementData, index + 1, elementData, index, j);// 把删除的元素后边的元素拷贝到删除元素位置，前移操作
        }
        modCount++;// 修改计数器增加（用于迭代器快速失败）
        elementCount--;// 元素数量减少
        elementData[elementCount] = null; /* to let gc do its work */
    }

    /**
     * 指定索引位置插入元素
     */
    public synchronized void insertElementAt(E obj, int index) {
        if (index > elementCount) {// 必须向已存在位置插入元素
            throw new ArrayIndexOutOfBoundsException(index
                    + " > " + elementCount);
        }
        modCount++;
        final int s = elementCount;// 获取现有元素数量
        Object[] elementData = this.elementData;// 获取现有元素数组
        if (s == elementData.length)// 如果现有容量满了
            elementData = grow();// 扩容操作，扩容增量小于等于0时2倍扩容，否则当前容量加增量扩容
        System.arraycopy(elementData, index,// 从index位置开始复制数组
                elementData, index + 1,// 复制到index+1的位置
                s - index);// 复制的个数
        elementData[index] = obj;// 插入的元素赋值
        elementCount = s + 1;// 元素个数+1
    }

    /**
     * 添加元素
     */
    public synchronized void addElement(E obj) {
        modCount++;
        add(obj, elementData, elementCount);// 添加到末尾
    }

    /**
     * 移除数组中首次出现的目标元素
     */
    public synchronized boolean removeElement(Object obj) {
        modCount++;
        int i = indexOf(obj);// 获取首次出现的目标元素索引（从前向后）
        if (i >= 0) {
            removeElementAt(i);// 移除元素并完成前移操作
            return true;
        }
        return false;
    }

    /**
     * 移除所有元素
     */
    public synchronized void removeAllElements() {
        final Object[] es = elementData;
        for (int to = elementCount, i = elementCount = 0; i < to; i++)// 循环移除
            es[i] = null;
        modCount++;
    }

    /**
     * Returns a clone of this vector. The copy will contain a
     * reference to a clone of the internal data array, not a reference
     * to the original internal data array of this {@code Vector} object.
     *
     * @return  a clone of this vector
     */
    public synchronized Object clone() {
        try {
            @SuppressWarnings("unchecked")
            Vector<E> v = (Vector<E>) super.clone();
            v.elementData = Arrays.copyOf(elementData, elementCount);
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    /**
     * Returns an array containing all of the elements in this Vector
     * in the correct order.
     *
     * @since 1.2
     */
    public synchronized Object[] toArray() {
        return Arrays.copyOf(elementData, elementCount);
    }

    /**
     * Returns an array containing all of the elements in this Vector in the
     * correct order; the runtime type of the returned array is that of the
     * specified array.  If the Vector fits in the specified array, it is
     * returned therein.  Otherwise, a new array is allocated with the runtime
     * type of the specified array and the size of this Vector.
     *
     * <p>If the Vector fits in the specified array with room to spare
     * (i.e., the array has more elements than the Vector),
     * the element in the array immediately following the end of the
     * Vector is set to null.  (This is useful in determining the length
     * of the Vector <em>only</em> if the caller knows that the Vector
     * does not contain any null elements.)
     *
     * @param <T> type of array elements. The same type as {@code <E>} or a
     * supertype of {@code <E>}.
     * @param a the array into which the elements of the Vector are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the Vector
     * @throws ArrayStoreException if the runtime type of a, {@code <T>}, is not
     * a supertype of the runtime type, {@code <E>}, of every element in this
     * Vector
     * @throws NullPointerException if the given array is null
     * @since 1.2
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> T[] toArray(T[] a) {
        if (a.length < elementCount)
            return (T[]) Arrays.copyOf(elementData, elementCount, a.getClass());

        System.arraycopy(elementData, 0, a, 0, elementCount);

        if (a.length > elementCount)
            a[elementCount] = null;

        return a;
    }

    // Positional Access Operations

    @SuppressWarnings("unchecked")
    E elementData(int index) {
        return (E) elementData[index];
    }

    @SuppressWarnings("unchecked")
    static <E> E elementAt(Object[] es, int index) {
        return (E) es[index];
    }

    /**
     * 根据指定索引获取目标元素
     */
    public synchronized E get(int index) {
        if (index >= elementCount)
            throw new ArrayIndexOutOfBoundsException(index);

        return elementData(index);
    }

    /**
     * 设置目标索引位置元素
     */
    public synchronized E set(int index, E element) {
        if (index >= elementCount)
            throw new ArrayIndexOutOfBoundsException(index);

        E oldValue = elementData(index);// 获取旧值
        elementData[index] = element;// 更新元素
        return oldValue;// 返回旧值
    }

    /**
     * 添加元素
     */
    private void add(E e, Object[] elementData, int s) {
        if (s == elementData.length)// 容量满了
            elementData = grow();// 扩容操作
        elementData[s] = e;// 最后的位置赋值
        elementCount = s + 1;// 现有元素个数+1
    }

    /**
     * 添加元素到数组尾部
     */
    public synchronized boolean add(E e) {
        modCount++;
        add(e, elementData, elementCount);
        return true;
    }

    /**
     * 删除元素
     */
    public boolean remove(Object o) {
        return removeElement(o);
    }

    /**
     * 添加元素到索引指定位置并完成后移操作
     */
    public void add(int index, E element) {
        insertElementAt(element, index);
    }

    /**
     * 删除指定索引位置的元素并可能会涉及元素前移操作
     */
    public synchronized E remove(int index) {
        modCount++;
        if (index >= elementCount)
            throw new ArrayIndexOutOfBoundsException(index);
        E oldValue = elementData(index);

        int numMoved = elementCount - index - 1;// 用于判断是否是尾部元素
        if (numMoved > 0)// 不是尾部元素
            System.arraycopy(elementData, index+1, elementData, index,
                    numMoved);// 后面的所有元素前移（这里与arraylist的代码基本一致）
        elementData[--elementCount] = null; // Let gc do its work

        return oldValue;// 返回旧值
    }

    /**
     * 添加元素集合
     */
    public boolean addAll(Collection<? extends E> c) {
        Object[] a = c.toArray();
        modCount++;
        int numNew = a.length;// 新元素集合大小
        if (numNew == 0)
            return false;
        synchronized (this) {
            Object[] elementData = this.elementData;
            final int s = elementCount;
            if (numNew > elementData.length - s)// 目标集合元素大于现有容量给
                elementData = grow(s + numNew);// 执行扩容操作
            System.arraycopy(a, 0, elementData, s, numNew);// 新集合拷贝到旧集合中
            elementCount = s + numNew;// 重置元素个数
            return true;
        }
    }
}

```

#### **CopyOnWriteArrayList**

![](pngs\copyonwritearraylist.png)

```java
package java.util.concurrent;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import jdk.internal.access.SharedSecrets;


public class CopyOnWriteArrayList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 8673264195747942595L;

    /**
     * 对象锁
     */
    final transient Object lock = new Object();

    /** 存储元素数组，私有属性 */
    private transient volatile Object[] array;

    /**
     * 获取数组
     */
    final Object[] getArray() {
        return array;
    }

    /**
     * 设置数组
     */
    final void setArray(Object[] a) {
        array = a;
    }

    /**
     * 无参构造函数，初始化一个空数组
     */
    public CopyOnWriteArrayList() {
        setArray(new Object[0]);
    }

    /**
     * 有参构造函数，初始化入参集合到数组中
     */
    public CopyOnWriteArrayList(Collection<? extends E> c) {
        Object[] es;
        if (c.getClass() == CopyOnWriteArrayList.class)
            es = ((CopyOnWriteArrayList<?>)c).getArray();// 如果入参是CopyOnWriteArrayList集合，es直接指向集合的数组属性
        else {
            es = c.toArray();// 指向c集合数组
            if (c.getClass() != java.util.ArrayList.class)// 如果c不是ArrayList
                es = Arrays.copyOf(es, es.length, Object[].class);// 重新拷贝入参集合到新的数组中并重置es
        }
        setArray(es);// 给予array赋值操作
    }

    /**
     * 有参构造函数，初始化入参数组到数组中
     */
    public CopyOnWriteArrayList(E[] toCopyIn) {
        setArray(Arrays.copyOf(toCopyIn, toCopyIn.length, Object[].class));// 拷贝出一个新的数组并赋值
    }

    /**
     * 获取数组大小
     */
    public int size() {
        return getArray().length;
    }

    /**
     * 获取数组是否为空
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * 获取指定元素范围内的目标元素索引（从前向后遍历）
     */
    private static int indexOfRange(Object o, Object[] es, int from, int to) {
        if (o == null) {// 目标元素为空
            for (int i = from; i < to; i++)// 从前向后遍历
                if (es[i] == null)// 如果元素为空
                    return i;// 返回索引
        } else {
            for (int i = from; i < to; i++)// 从前向后遍历
                if (o.equals(es[i]))// 如果元素等于目标元素
                    return i;// 返回索引
        }
        return -1;
    }

    /**
     * 获取指定元素范围内的目标元素索引（从后向前遍历）
     */
    private static int lastIndexOfRange(Object o, Object[] es, int from, int to) {
        if (o == null) {// 目标元素为空
            for (int i = to - 1; i >= from; i--)// 从后向前遍历
                if (es[i] == null)// 如果等于空
                    return i;// 返回索引
        } else {
            for (int i = to - 1; i >= from; i--)// 从后向前遍历
                if (o.equals(es[i]))// 如果等于目标元素
                    return i;// 返回索引
        }
        return -1;
    }

    /**
     * 从前向后遍历判断是否包含目标元素
     */
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * 获取目标元素索引
     */
    public int indexOf(Object o) {// 目标对象
        Object[] es = getArray();// 获取当前数组
        return indexOfRange(o, es, 0, es.length);// 从头到尾获取目标元素索引
    }

    /**
     * 根据目标元素以及入参索引获取目标元素索引位置
     */
    public int indexOf(E e, int index) {
        Object[] es = getArray();// 获取数组
        return indexOfRange(e, es, index, es.length);// 从前向后获取目标索引
    }

    /**
     * 从后向前获取目标索引
     */
    public int lastIndexOf(Object o) {
        Object[] es = getArray();// 获取数组
        return lastIndexOfRange(o, es, 0, es.length);// 从后向前获取目标索引
    }

    /**
     * 从后向前根据入参索引获取目标索引元素位置
     */
    public int lastIndexOf(E e, int index) {
        Object[] es = getArray();// 获取数组
        return lastIndexOfRange(e, es, 0, index + 1);// 从后向前获取目标索引
    }

    /**
     * 浅克隆，引用的对象还都是原对象
     * 提问，既然是浅拷贝，克隆出来的新集合修改会影响原本的集合吗？或者原本的集合修改会影响新的集合吗？
     * 答案是不会的，原数组会复制一份新的给自己用，克隆的列表仍然引用旧数组，从此分道扬镳，各自独立，这也是写时复制的命脉
     */
    public Object clone() {
        try {
            @SuppressWarnings("unchecked")
            CopyOnWriteArrayList<E> clone =
                    (CopyOnWriteArrayList<E>) super.clone();
            clone.resetLock();// 确保得到的 CopyOnWriteArrayList 实例都有自己独立的锁对象
            // Unlike in readObject, here we cannot visibility-piggyback on the
            // volatile write in setArray().
            VarHandle.releaseFence();
            return clone;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /**
     * 转数组
     */
    public Object[] toArray() {
        return getArray().clone();
    }

    /**
     * 把当前集合中的元素复制到传入数组中，如果传入数组有值，则会覆盖
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        Object[] es = getArray();
        int len = es.length;
        if (a.length < len)
            return (T[]) Arrays.copyOf(es, len, a.getClass());
        else {
            System.arraycopy(es, 0, a, 0, len);
            if (a.length > len)
                a[len] = null;
            return a;
        }
    }

    // Positional Access Operations

    @SuppressWarnings("unchecked")
    static <E> E elementAt(Object[] a, int index) {
        return (E) a[index];
    }

    static String outOfBounds(int index, int size) {
        return "Index: " + index + ", Size: " + size;
    }

    /**
     * 获取指定索引下标元素
     */
    public E get(int index) {
        return elementAt(getArray(), index);
    }

    /**
     * 更新指定索引元素
     */
    public E set(int index, E element) {
        synchronized (lock) {
            Object[] es = getArray();// 获取临时数组
            E oldValue = elementAt(es, index);// 获取指定索引元素

            if (oldValue != element) {// 比较引用地址
                es = es.clone();// 创建数组副本（写时复制）
                es[index] = element;// 更新指定索引元素
            }
            // Ensure volatile write semantics even when oldvalue == element
            setArray(es);// 重新赋值，这保证了volatile写语义，确保修改对其他线程立即可见
            return oldValue;// 返回旧值
        }
    }

    /**
     * 添加元素
     */
    public boolean add(E e) {
        synchronized (lock) {
            Object[] es = getArray();// 获取临时数组
            int len = es.length;// 获取临时数组大小
            es = Arrays.copyOf(es, len + 1);// 拷贝一份+1的数组并赋值给es
            es[len] = e;// 尾部赋值
            setArray(es);// 重新赋值，这保证了volatile写语义，确保修改对其他线程立即可见
            return true;
        }
    }

    /**
     * 指定索引位置添加元素
     */
    public void add(int index, E element) {
        synchronized (lock) {
            Object[] es = getArray();// 获取临时数组
            int len = es.length;// 获取临时数组长度
            if (index > len || index < 0)// 如果插入的位置不在当前数组有效范围内
                throw new IndexOutOfBoundsException(outOfBounds(index, len));// 抛出数组越界异常
            Object[] newElements;// 新元素数组
            int numMoved = len - index;// 数组长度减去指定索引下标，获取需要移动元素数量
            if (numMoved == 0)// 如果等于零，说明在数组尾部插入，不需要移动
                newElements = Arrays.copyOf(es, len + 1);// 创建容量+1的新数组
            else {
                newElements = new Object[len + 1];// 构建新数组大小在原有基础上+1
                System.arraycopy(es, 0, newElements, 0, index);// 复制插入位置前的元素（0 ~ index-1）
                System.arraycopy(es, index, newElements, index + 1,
                        numMoved);// 将这些元素向后移动一位（从index+1位置开始存放）
            }
            newElements[index] = element;// 指定索引元素重新赋值
            setArray(newElements);// 重新赋值，这保证了volatile写语义，确保修改对其他线程立即可见
        }
    }

    /**
     * 删除指定索引位置的元素
     */
    public E remove(int index) {
        synchronized (lock) {
            Object[] es = getArray();// 获取临时数组
            int len = es.length;// 获取临时数组大小
            E oldValue = elementAt(es, index);// 获取指定索引的旧值
            int numMoved = len - index - 1;// 需要移动的个数
            Object[] newElements;// 新数组
            if (numMoved == 0)// 要删除的是最后一个元素
                newElements = Arrays.copyOf(es, len - 1); // 创建新数组，只包含前 len-1 个元素
            else {
                newElements = new Object[len - 1];// 定义新数组大小在原基础上-1
                System.arraycopy(es, 0, newElements, 0, index);// 拷贝删除位置前的元素
                System.arraycopy(es, index + 1, newElements, index,
                        numMoved);// 拷贝删除位置后的元素（整体前移一个单位）
            }
            setArray(newElements);// 重新赋值，这保证了volatile写语义，确保修改对其他线程立即可见
            return oldValue;
        }
    }

    /**
     * 指定元素删除
     */
    public boolean remove(Object o) {
        Object[] snapshot = getArray();// 获取临时数组
        int index = indexOfRange(o, snapshot, 0, snapshot.length);// 获取临时数组删除目标索引
        return index >= 0 && remove(o, snapshot, index);// 执行删除操作
    }

    /**
     * 根据索引删除指定元素
     */
    private boolean remove(Object o, Object[] snapshot, int index) {
        synchronized (lock) {
            Object[] current = getArray();// 获取临时数组
            int len = current.length;// 获取临时数组大小
            if (snapshot != current) findIndex: {// 如果快照已过期（数组已被其他线程修改）
                int prefix = Math.min(index, len);// 先比较数组前缀，看元素是否前移了
                for (int i = 0; i < prefix; i++) {
                    if (current[i] != snapshot[i]// 位置有变化
                            && Objects.equals(o, current[i])) {// 且找到目标元素
                        index = i;// 入参索引重置
                        break findIndex;// 跳出标签块
                    }
                }
                if (index >= len)// 如果没在前缀找到，检查原索引位置
                    return false;// 索引越界
                if (current[index] == o)// 原位置就是目标元素
                    break findIndex;// 跳出标签块
                index = indexOfRange(o, current, index, len);// 在原索引位置后面查找
                if (index < 0)
                    return false;// 没找到
            }
            Object[] newElements = new Object[len - 1];// 定义新数组大小为原数组大小-1
            System.arraycopy(current, 0, newElements, 0, index);// 拷贝删除之前的元素
            System.arraycopy(current, index + 1,
                    newElements, index,
                    len - index - 1);// 拷贝删除之后的元素，整齐前移一个单位
            setArray(newElements);// 重新赋值，这保证了volatile写语义，确保修改对其他线程立即可见
            return true;
        }
    }

    /**
     * 删除指定范围的元素
     */
    void removeRange(int fromIndex, int toIndex) {
        synchronized (lock) {
            Object[] es = getArray();// 获取临时数组
            int len = es.length;// 获取临时数组长度

            if (fromIndex < 0 || toIndex > len || toIndex < fromIndex)
                throw new IndexOutOfBoundsException();// 合法性校验
            int newlen = len - (toIndex - fromIndex);// 剩余数组大小
            int numMoved = len - toIndex;// 数组移动单位个数
            if (numMoved == 0)// 不需要移动
                setArray(Arrays.copyOf(es, newlen));// 直接拷贝0-newlen个单位的元素并重新赋值
            else {
                Object[] newElements = new Object[newlen];// 定义newlen新数组
                System.arraycopy(es, 0, newElements, 0, fromIndex);// 复制前半部分：从0到fromIndex（不包含fromIndex）
                System.arraycopy(es, toIndex, newElements,
                        fromIndex, numMoved);// 复制后半部分：从toIndex到结尾，放到新数组的fromIndex位置
                setArray(newElements);// 重新赋值
            }
        }
    }

    /**
     * 如果元素不存在当前数组中则添加目标元素
     */
    public boolean addIfAbsent(E e) {
        Object[] snapshot = getArray();// 获取临时数组
        return indexOfRange(e, snapshot, 0, snapshot.length) < 0// true-不存在
                && addIfAbsent(e, snapshot);// 不存在则添加元素
    }

    /**
     * 集合中不存在目标数据则添加
     */
    private boolean addIfAbsent(E e, Object[] snapshot) {
        synchronized (lock) {
            Object[] current = getArray();// 获取临时数组
            int len = current.length;// 获取临时数组长度
            if (snapshot != current) {// 如果快照数组不等于当前数组
                // Optimize for lost race to another addXXX operation
                int common = Math.min(snapshot.length, len);// 快照已过期，需要检查差异
                for (int i = 0; i < common; i++)
                    if (current[i] != snapshot[i]// 引用不同（说明被修改过）
                            && Objects.equals(e, current[i]))// 且正好是我们想添加的元素
                        return false;// 元素已存在
                if (indexOfRange(e, current, common, len) >= 0)// 检查新增的部分（len可能比snapshot.length大）
                    return false;
            }
            Object[] newElements = Arrays.copyOf(current, len + 1);// 通过拷贝创建新数组，大小+1
            newElements[len] = e;// 数组尾部添加元素
            setArray(newElements);// 数组重新赋值
            return true;
        }
    }

    /**
     * 判断是否包含目标集合
     */
    public boolean containsAll(Collection<?> c) {
        Object[] es = getArray();// 获取临时数组
        int len = es.length;// 获取临时数组容量
        for (Object e : c) {
            if (indexOfRange(e, es, 0, len) < 0)// 不包含
                return false;
        }
        return true;// 包含
    }

    /**
     * 删除数组中指定集合中的元素
     */
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);// 如果入参为空，抛空指针异常
        return bulkRemove(e -> c.contains(e));// 判断目标集合是否包含
    }

    /**
     * 从 该列表中移除所有不包含在指定集合中的元素
     */
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));// 如果不包含则删除
    }

    /**
     * 将指定集合中尚未包含在该列表中的元素，按该集合的迭代器返回的顺序，附加到列表末尾
     */
    public int addAllAbsent(Collection<? extends E> c) {
        Object[] cs = c.toArray();// 获取临时数组
        if (c.getClass() != ArrayList.class) {
            cs = cs.clone();// 如果不是ArrayList直接重新赋值
        }
        if (cs.length == 0)// 如果集合为空，直接返回
            return 0;
        synchronized (lock) {
            Object[] es = getArray();// 获取当前数组副本
            int len = es.length;// 获取副本容量
            int added = 0;
            // uniquify and compact elements in cs
            for (int i = 0; i < cs.length; ++i) {
                Object e = cs[i];
                if (indexOfRange(e, es, 0, len) < 0 &&// 当前列表中不存在e
                        indexOfRange(e, cs, 0, added) < 0)// 避免添加重复元素
                    cs[added++] = e;// 将不重复的元素压缩到cs数组中
            }
            if (added > 0) {
                Object[] newElements = Arrays.copyOf(es, len + added);// 创建新数组以及初始化大小为原数组大小+新添加元素数量
                System.arraycopy(cs, 0, newElements, len, added);// 拷贝到新数组
                setArray(newElements);// 重新赋值
            }
            return added;// 返回添加数量
        }
    }

    /**
     * 清空数组
     */
    public void clear() {
        synchronized (lock) {
            setArray(new Object[0]);
        }
    }

    /**
     * 添加集合到当前数组尾部
     * @see #add(Object)
     */
    public boolean addAll(Collection<? extends E> c) {
        Object[] cs = (c.getClass() == CopyOnWriteArrayList.class) ?
                ((CopyOnWriteArrayList<?>)c).getArray() : c.toArray();// 获取添加集合数组
        if (cs.length == 0)// 如果大小为0直接返回
            return false;
        synchronized (lock) {
            Object[] es = getArray();// 获取当前数组
            int len = es.length;// 获取当前数组容量
            Object[] newElements;
            if (len == 0 && (c.getClass() == CopyOnWriteArrayList.class ||
                    c.getClass() == ArrayList.class)) {// 如果当前数组大小为0并且类型为CopyOnWriteArrayList或者ArrayList
                newElements = cs;// 新数组直接指向老数组
            } else {
                newElements = Arrays.copyOf(es, len + cs.length);// 否则拷贝老数组以及初始化容量为老数组大小+新数组大小
                System.arraycopy(cs, 0, newElements, len, cs.length);// 然后再把新数组中的数据拷贝到新数组尾部（此时新数组已经包含的老数据）
            }
            setArray(newElements);// 重新赋值
            return true;
        }
    }

    /**
     * 在指定索引位置添加新集合
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        Object[] cs = c.toArray();// 获取集合数组
        synchronized (lock) {
            Object[] es = getArray();// 获取当前数组
            int len = es.length;// 获取当前数组大小
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException(outOfBounds(index, len));// 索引合法性校验
            if (cs.length == 0)
                return false;
            int numMoved = len - index;// 获取需要后移的个数
            Object[] newElements;
            if (numMoved == 0)// 添加到尾部
                newElements = Arrays.copyOf(es, len + cs.length);// 拷贝老数组到新数组以及初始化新数组容量为老数组大小+新数组大小
            else {
                newElements = new Object[len + cs.length];// 初始化新数组以及容量为老数组大小+新数组大小
                System.arraycopy(es, 0, newElements, 0, index);// 拷贝0-index个长度到新数组中
                System.arraycopy(es, index,
                        newElements, index + cs.length,
                        numMoved);// 拷贝index+cs.length-尾部个单位额到新数组中
                // 此处代码可以理解为把老数组拆分为两份，中间预留出来要插入的数组部分
            }
            System.arraycopy(cs, 0, newElements, index, cs.length);// 此时老数组已经在newElements中了，把新数组拷贝到指定索引位置
            setArray(newElements);// 重新赋值
            return true;
        }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        for (Object x : getArray()) {
            @SuppressWarnings("unchecked") E e = (E) x;
            action.accept(e);
        }
    }

    /**
     * 满足条件则删除
     */
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }

    // A tiny bit set implementation

    private static long[] nBits(int n) {
        return new long[((n - 1) >> 6) + 1];
    }
    private static void setBit(long[] bits, int i) {
        bits[i >> 6] |= 1L << i;
    }
    private static boolean isClear(long[] bits, int i) {
        return (bits[i >> 6] & (1L << i)) == 0;
    }

    private boolean bulkRemove(Predicate<? super E> filter) {
        synchronized (lock) {
            return bulkRemove(filter, 0, getArray().length);// 遍历去删除
        }
    }

    boolean bulkRemove(Predicate<? super E> filter, int i, int end) {
        // assert Thread.holdsLock(lock);
        final Object[] es = getArray(); // 获取当前数组的快照
        // Optimize for initial run of survivors
        // 第一轮：跳过前面不需要删除的元素
        // 找到第一个需要删除的元素位置
        for (; i < end && !filter.test(elementAt(es, i)); i++)
            ;
        if (i < end) {// 如果找到了需要删除的元素
            final int beg = i;// 记录第一个删除位置
            final long[] deathRow = nBits(end - beg);// 创建位图记录哪些元素要删除
            int deleted = 1;// 已删除计数（第一个元素要删除）
            deathRow[0] = 1L;// 设置位图第一个位（标记 beg 位置的元素要删除）
            // 第二轮：扫描剩余元素，标记所有需要删除的元素
            for (i = beg + 1; i < end; i++)
                if (filter.test(elementAt(es, i))) {// 调用 Lambda 判断
                    setBit(deathRow, i - beg);// 在位图中标记
                    deleted++;// 增加删除计数
                }
            // 检查数组是否被并发修改（CopyOnWriteArrayList 的特性）
            if (es != getArray())
                throw new ConcurrentModificationException();
            // 创建新数组（长度 = 原长度 - 删除数量）
            final Object[] newElts = Arrays.copyOf(es, es.length - deleted);
            int w = beg;// 新数组的写入位置
            for (i = beg; i < end; i++)
                if (isClear(deathRow, i - beg))// 如果位图显示该元素不需要删除
                    newElts[w++] = es[i];// 拷贝到新数组
            // 拷贝 [end, 原长度) 区间的元素（这些在扫描范围之外）
            System.arraycopy(es, i, newElts, w, es.length - i);
            // 设置新数组，完成写时复制
            setArray(newElts);
            return true;// 返回 true 表示有修改
        } else {
            if (es != getArray())
                throw new ConcurrentModificationException();
            return false; // 返回 false 表示没有修改
        }
    }

    /**
     * 函数式接口替换所有元素例如
     * scores.replaceAll(score -> score < 60 ? 60 : score);
     */
    public void replaceAll(UnaryOperator<E> operator) {
        synchronized (lock) {
            replaceAllRange(operator, 0, getArray().length);// 替换从0-length的元素
        }
    }

    void replaceAllRange(UnaryOperator<E> operator, int i, int end) {
        // assert Thread.holdsLock(lock);
        Objects.requireNonNull(operator);
        final Object[] es = getArray().clone();// 获取副本
        for (; i < end; i++)
            es[i] = operator.apply(elementAt(es, i));// 获取指定索引的元素并执行apply执行自定义lambda方法获取新值并更新
        setArray(es);// 重新赋值
    }

    public void sort(Comparator<? super E> c) {
        synchronized (lock) {
            sortRange(c, 0, getArray().length);// 排序从0-length的元素
        }
    }

    @SuppressWarnings("unchecked")
    void sortRange(Comparator<? super E> c, int i, int end) {
        // assert Thread.holdsLock(lock);
        final Object[] es = getArray().clone();// 获取副本
        Arrays.sort(es, i, end, (Comparator<Object>)c);// 根绝掺入的lambda进行排序
        setArray(es);// 重新赋值
    }

}

```



### 2.2 Set接口
#### **HashSet**

![](pngs\hashset.png)

```java
package java.util;

import java.io.InvalidObjectException;
import jdk.internal.access.SharedSecrets;

public class HashSet<E>
        extends AbstractSet<E>
        implements Set<E>, Cloneable, java.io.Serializable
{
    @java.io.Serial
    static final long serialVersionUID = -5024744406713321676L;

    // HashSet整体基于HashMap的key作为主要实现
    private transient HashMap<E,Object> map;

    // 用虚拟值与背景映射中的对象关联
    private static final Object PRESENT = new Object();

    /**
     * 构造一个新的空集合;支持的{@code HashMap}实例默认初始容量为16，负载因子为0.75
     */
    public HashSet() {
        map = new HashMap<>();
    }

    /**
     * 构造包含指定集合中元素的新集合。该集合 HashMap 以默认负载因子（0.75）和足以容纳指定集合中元素的初始容量创建
     */
    public HashSet(Collection<? extends E> c) {
        map = new HashMap<>(Math.max((int) (c.size()/.75f) + 1, 16));
        addAll(c);
    }

    /**
     * 构造一个新的空集合;后备 HashMap 实例具有指定的初始容量和负载因子
     */
    public HashSet(int initialCapacity, float loadFactor) {
        map = new HashMap<>(initialCapacity, loadFactor);
    }

    /**
     * 构造一个新的空集合;后备 HashMap 实例具有指定的初始容量和默认负载因子（0.75）
     */
    public HashSet(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }

    /**
     * 构造一个新的空链接哈希集。（该包私有构造器仅由 LinkedHashSet 使用。）支持的HashMap实例是一个具有指定初始容量和负载因子的LinkedHashMap
     */
    HashSet(int initialCapacity, float loadFactor, boolean dummy) {
        map = new LinkedHashMap<>(initialCapacity, loadFactor);
    }

    /**
     * 返回该集合中元素的迭代器。元素的返回顺序没有特定顺
     */
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    /**
     * 返回该集合中的元素数
     */
    public int size() {
        return map.size();
    }

    /**
     * 如果该集合不包含元素，则返回 tru
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * 如果该集合包含指定元素，则返回 true 。更正式地说，当 true 且仅当该集合包含 e 一个元素使得 Objects. equals(o, e)
     */
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    /**
     * 如果指定元素尚未存在，则将其添加到该集合中。更正式地说，如果该集合中没有e元素使Objects. equals(e, e2)得 ，
     * 则将指定的元素e加入该集合。如果该集合已经包含该元素，调用则保持集合不变并返回 false
     */
    public boolean add(E e) {
        return map.put(e, PRESENT)==null;
    }

    /**
     * 如果指定元素存在，则从该集合中移除该元素
     */
    public boolean remove(Object o) {
        return map.remove(o)==PRESENT;
    }

    /**
     * 移除了这套中的所有元素。该调用返回后集合将为空
     */
    public void clear() {
        map.clear();
    }

    /**
     * 返回的是此 HashSet 实例的浅显副本：元素本身未被克隆
     */
    @SuppressWarnings("unchecked")
    public Object clone() {
        try {
            HashSet<E> newSet = (HashSet<E>) super.clone();
            newSet.map = (HashMap<E, Object>) map.clone();
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
    
}

```



#### **LinkedHashSet**
![](pngs\linkedhashset.png)

```java
package java.util;

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
```

#### **TreeSet**

- 2.2.4.1 基于红黑树实现
- 2.2.4.2 排序功能
- 2.2.4.3 使用场景

#### **并发Set**
- 2.2.5.1 CopyOnWriteArraySet
- 2.2.5.2 ConcurrentSkipListSet

### 2.3 Queue接口
- 2.3.1 Queue接口特性

#### **Deque接口**
##### **ArrayDeque**
- 2.3.2.1 循环数组实现
- 2.3.2.2 性能特点

#### **PriorityQueue**
- 2.3.3.1 堆实现原理
- 2.3.3.2 优先级排序

#### **BlockingQueue接口**
- 2.3.4.1 阻塞队列特性

##### **ArrayBlockingQueue**
- 2.3.5.1 有界阻塞队列

##### **LinkedBlockingQueue**
- 2.3.6.1 链表阻塞队列

##### **PriorityBlockingQueue**
- 2.3.7.1 无界优先级队列

##### **DelayQueue**
- 2.3.8.1 延迟队列

##### **SynchronousQueue**
- 2.3.9.1 同步移交队列

## 三、Map接口体系

### 3.1 HashMap

![](pngs\hashmap.png)

```java
package java.util;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.internal.access.SharedSecrets;

public class HashMap<K,V> extends AbstractMap<K,V>
        implements Map<K,V>, Cloneable, Serializable {

    /**
     * 初始容量-必须是2的幂
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * 最大容量，指任一构造子通过参数隐式指定更高值。必须是2的幂<= 1<<30
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 当构造器中未指定载重因子时所用的载重因子
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * 当链表长度 ≥ 8（TREEIFY_THRESHOLD）时，链表转为红黑树（树化）
     */
    static final int TREEIFY_THRESHOLD = 8;

    /**
     * 当树节点数 ≤ 6（UNTREEIFY_THRESHOLD）时，红黑树转回链表（降级
     */
    static final int UNTREEIFY_THRESHOLD = 6;

    /**
     * 只有当HashMap的容量（桶数组长度）至少达到64时，才允许链表转树
     * 否则，即使链表长度达到8，也会优先选择扩容而非树化
     */
    static final int MIN_TREEIFY_CAPACITY = 64;

    /**
     * Node的基础地位：HashMap存储的默认选择
     * TreeNode的特殊性：性能优化时的替代结构
     * LinkedHashMap.Entry的关系：顺序Map的扩展基础
     */
    static class Node<K,V> implements Map.Entry<K,V> {
        // hash: 经过扰动函数处理后的哈希值（不是key.hashCode()的原始值）
        //    - final修饰：一旦创建不可更改，保证一致性
        //    - 缓存：避免每次比较时重新计算hashCode
        //    - HashMap的扰动处理：h = key.hashCode() ^ (h >>> 16)
        final int hash;
        // key: 键对象
        //    - final修饰：键不可变！如果键可变且修改了hashCode，会破坏HashMap结构
        //    - 不可为null（如果HashMap不允许null键）
        final K key;
        // value: 值对象
        //    - 非final：可以通过setValue()修改
        //    - 可以为null（HashMap允许null值）
        V value;
        // next: 指向下一个Node的引用
        //    - 实现链地址法解决哈希冲突
        //    - null表示链表结束
        //    - 在树化时，TreeNode仍然保留next指针（退化链表时需要）
        Node<K,V> next;// 指向下一个node

        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        // final方法：禁止子类重写，保证所有Node行为一致
        public final K getKey()        { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value; }

        // 设计原理：
        // 1. Objects.hashCode(): null安全，null返回0
        // 2. 异或(^): 选择原因：
        //    a) 均匀分布：位运算混合效果好
        //    b) 可交换：key^value == value^key（虽然Map.Entry不要求）
        //    c) 不会溢出：位运算无溢出问题
        //    d) 性能好：单CPU指令
        // 3. 满足契约：如果 e1.equals(e2)，则 e1.hashCode() == e2.hashCode()
        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {
            if (o == this)
                return true;

            return o instanceof Map.Entry<?, ?> e
                    && Objects.equals(key, e.getKey())
                    && Objects.equals(value, e.getValue());
        }
    }

    /* ---------------- Static utilities -------------- */

    /**
     * 计算键的哈希值（扰动函数）
     * 目的：将key.hashCode()的高位特征混合到低位，减少哈希碰撞
     */
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);// 将哈希码的高位特征扩散到低位，提高分布均匀性，显著减少哈希碰撞，尤其当table较小时，高16位与低16位异或
    }

    /**
     * 如果x的类形式为“类Comparable实现可比类” ，则返回x的类 “，否则无效。
     */
    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            Class<?> c; Type[] ts, as; ParameterizedType p;
            /**
             * String实现了Comparable<String>，并且非常常用
             * 直接返回String.class，跳过复杂的反射检查
             * 性能优化：String比较很常见，直接快速返回
             */
            if ((c = x.getClass()) == String.class) // bypass checks
                return c;
            if ((ts = c.getGenericInterfaces()) != null) {// 获取类的所有泛型接口
                for (Type t : ts) {// 遍历每个接口
                    if ((t instanceof ParameterizedType) &&// 检查条件1：是参数化类型（带泛型）
                            ((p = (ParameterizedType) t).getRawType() ==
                                    Comparable.class) &&// 条件2：原始类型是Comparable
                            (as = p.getActualTypeArguments()) != null &&// 条件3：有实际的类型参数
                            as.length == 1 && as[0] == c) // 条件4：只有一个类型参数
                        return c;// 满足所有条件，返回类对象
                }
            }
        }
        return null;
    }

    /**
     * 如果 x 与 kc 匹配（k 的筛选类），则返回 k. compareTo（x），否则 0
     */
    @SuppressWarnings({"rawtypes","unchecked"}) // for cast to Comparable
    static int compareComparables(Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc ? 0 :
                ((Comparable)k).compareTo(x));
    }

    /**
     * 返回大于等于cap的最小2的幂
     * 例如：cap=10 → 返回16，cap=17 → 返回32
     * HashMap要求容量必须是2的幂（为了高效的位运算取模）
     */
    static final int tableSizeFor(int cap) {
        // 核心算法：将cap-1的最高位之后的所有位都设为1，然后+1
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /* ---------------- Fields -------------- */

    /**
     * 桶数组
     */
    transient Node<K,V>[] table;

    /**
     * 保留缓存的entrySet（）。注意，AbstractMap 字段用于 keySet（） 和 values（）
     */
    transient Set<Map.Entry<K,V>> entrySet;

    /**
     * 该映射中包含的键值映射数量
     */
    transient int size;

    /**
     * 该哈希图被结构性修改的次数 结构性修改是指改变哈希图中映射数量或以其他方式修改其内部结构（例如，重写）。该字段用于使哈希图集合视图上的迭代器实现快速失败
     */
    transient int modCount;

    /**
     * 扩容阈值
     */
    int threshold;

    /**
     * 哈希表的负载因子
     */
    final float loadFactor;

    /* ---------------- Public operations -------------- */

    /**
     * 构造具备指定初始容量和负荷因子的空体 HashMap
     *
     * @param  initialCapacity 初始容量
     * @param  loadFactor      负载因子
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    initialCapacity);// 初始化容量校验
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;// 容量最大值为1<<30
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                    loadFactor);// 负载因子校验
        this.loadFactor = loadFactor;// 初始化负载因子
        this.threshold = tableSizeFor(initialCapacity);
    }

    /**
     * 构造一个具有指定初始容量和默认负载因子（0.75）的空体 HashMap
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 构造一个空舱 HashMap ，初始容量为16，负载因子为0.75
     */
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
    }

    /**
     * 构造一个具有与指定Map映射相同的新 HashMap 。HashMap该 以默认负载因子（0.75）和足够初始容量存储映射Map的初始容量创建
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        putMapEntries(m, false);
    }

    /**
     * 实现Map.putAll和Map构造函数
     *
     * @param m 要插入的Map
     * @param evict false表示在构造函数中调用，true表示在putAll中调用
     *              （传递给afterNodeInsertion方法，用于LinkedHashMap）
     */
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size();// 获取插入map大小
        if (s > 0) {
            if (table == null) { // 如果桶数组为空，初始化
                float ft = ((float)s / loadFactor) + 1.0F;// 计算所需容量：元素数/负载因子 + 1
                int t = ((ft < (float)MAXIMUM_CAPACITY) ?
                        (int)ft : MAXIMUM_CAPACITY);// 如果所需容量小于最大容量直接使用所需容量否则使用最大容量
                if (t > threshold)// 如果所需容量大于阈值
                    threshold = tableSizeFor(t);// 初始化阈值
            } else {
                while (s > threshold && table.length < MAXIMUM_CAPACITY)// 如果插入map大于阈值并且桶数组小于最大容量
                    resize();// 扩容以及node重新分配
            }

            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {// 遍历map插入
                K key = e.getKey();// 获取node的key
                V value = e.getValue();// 获取node的value
                putVal(hash(key), key, value, false, evict);// 插入
            }
        }
    }

    /**
     * 返回该映射中的键值映射数量
     */
    public int size() {
        return size;
    }

    /**
     * 返回该集合是否为空
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 返回指定密钥映射的值，若映射中没有该密钥映射，则返回null
     */
    public V get(Object key) {
        Node<K,V> e;
        return (e = getNode(key)) == null ? null : e.value;
    }

    /**
     * 根据key获取目标node
     */
    final Node<K,V> getNode(Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n, hash; K k;
        if ((tab = table) != null && (n = tab.length) > 0 &&// 桶数组为不空并且桶的容量大于0
                (first = tab[(n - 1) & (hash = hash(key))]) != null) {// 通过按位与计算获取桶数组中的位置获取目标元素
            if (first.hash == hash && // 确认了在桶中的位置并比较第一个node
                    ((k = first.key) == key || (key != null && key.equals(k))))// 如果key是要找的目标元素
                return first;// 直接返回
            // ---走到这里说明头节点不是要找的目标元素
            if ((e = first.next) != null) {
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);// 遍历红黑树返回目标元素
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);// 遍历链表返回目标元素
            }
        }
        return null;
    }

    /**
     * 如果该映射包含指定键的映射，则返回true
     */
    public boolean containsKey(Object key) {
        return getNode(key) != null;
    }

    /**
     * 将指定值与该映射中的指定键关联。如果映射之前包含键的映射，旧值会被替换
     */
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * – 哈希 for 键 key —钥匙
     * value – 要放置的价值
     * onlyIfAbsent —如果属实，不要改变现有价值
     * evict 如果为假，表示该表处于创建模式
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        if ((tab = table) == null || (n = tab.length) == 0)// 如果桶数组为空或长度为0
            n = (tab = resize()).length;// 通过resize()进行初始化
        if ((p = tab[i = (n - 1) & hash]) == null)// 计算索引位置，如果该位置为空
            tab[i] = newNode(hash, key, value, null);// 直接创建新节点放入
        else {// 该位置已有元素，处理hash冲突
            Node<K,V> e; K k;
            if (p.hash == hash &&// 先检查头节点是否匹配
                    ((k = p.key) == key || (key != null && key.equals(k))))// 如果就在头节点
                e = p;// 头节点就是要找的key，记录该节点
            else if (p instanceof TreeNode)// 如果是红黑树
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);// 在红黑树中查找或插入
            else {// 否则是链表结构，遍历链表查找
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {// 遍历到链表尾部，说明key不存在
                        p.next = newNode(hash, key, value, null);// 在链表尾部插入新节点
                        if (binCount >= TREEIFY_THRESHOLD - 1) // 如果链表长度达到树化阈值(8)
                            treeifyBin(tab, hash);// 尝试将链表转为红黑树（需检查数组长度）
                        break;
                    }
                    if (e.hash == hash &&// 找到了相同的key
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        break;// 跳出循环，此时e就是要找的节点
                    p = e;// 继续遍历下一个节点
                }
            }
            if (e != null) { // 更新操作，这一上边是尾节点插入
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }

        ++modCount;// 修改次数+1
        if (++size > threshold)// // 键值对数量超过阈值，触发扩容
            resize();// 扩容操作
        afterNodeInsertion(evict);
        return null;
    }

    /**
     * 初始化或加倍表大小。如果为零，则根据字段阈值中初始容量目标进行分配。否则，由于我们使用了二的幂展开，每个箱中的元素必须保持相同的索引，或者在新表中以二的幂次方偏移移动
     */
    final Node<K,V>[] resize() {
        Node<K,V>[] oldTab = table;// 获取临时桶数组
        int oldCap = (oldTab == null) ? 0 : oldTab.length;// 获取容量
        int oldThr = threshold;// 获取阈值
        int newCap, newThr = 0;// 初始化新的容量以及阈值

        // ========== 情况1：已有数据，正常扩容 ==========
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {// 如果旧容量大于等于最大容量
                threshold = Integer.MAX_VALUE;// 将阈值设为int最大值，禁止再扩容
                return oldTab;// 直接返回旧数组，不再扩容
            }
            // 正常扩容：容量翻倍（左移1位相当于乘以2）
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&// 新容量=旧容量×2，且小于最大容量
                    oldCap >= DEFAULT_INITIAL_CAPACITY)// 并且旧容量至少为默认初始容量(16)
                newThr = oldThr << 1; // 新阈值也翻倍（阈值=容量×负载因子，容量翻倍所以阈值也翻倍）
        }
        // ========== 情况2：通过构造函数指定了初始容量，第一次put ==========
        else if (oldThr > 0) // 旧容量=0但旧阈值>0，说明是通过HashMap(int)或HashMap(int,float)构造的
            newCap = oldThr;// 新容量 = 旧阈值（构造函数中threshold临时存储了初始容量）
        // ========== 情况3：无参构造函数，第一次put ==========
        else {// 旧容量=0且旧阈值=0，说明是通过HashMap()无参构造的
            newCap = DEFAULT_INITIAL_CAPACITY;// 新容量 = 默认初始容量(16)
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);// 新阈值 = 16 × 0.75 = 12
        }
        // ========== 情况4：新阈值还没有被计算（处理边界情况） ==========
        if (newThr == 0) {// 如果新阈值还是0（比如情况2中没有计算阈值）
            float ft = (float)newCap * loadFactor;// 计算理论阈值：新阈值 = 16 × 0.75 = 12
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                    (int)ft : Integer.MAX_VALUE);// 如果新容量或计算值超过最大容量，使用int最大值
        }
        threshold = newThr;// 更新全局阈值
        @SuppressWarnings({"rawtypes","unchecked"})
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];// 定义新的桶数组
        table = newTab;// 全局桶数组重新指向
        if (oldTab != null) {// 如果旧的桶数组不为空
            for (int j = 0; j < oldCap; ++j) {// 遍历旧的桶数组
                Node<K,V> e;// 当前桶的头节点
                if ((e = oldTab[j]) != null) {// 如果当前桶不为空
                    oldTab[j] = null;// 清空旧桶
                    // ===== 子情况1：桶中只有一个节点 =====
                    if (e.next == null)// 如果当前桶只有一个节点
                        newTab[e.hash & (newCap - 1)] = e;// 重新计算桶位置并指向
                    // ===== 子情况2：桶中是红黑树 =====
                    else if (e instanceof TreeNode)// 如果节点是树节点
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);// 调用TreeNode的split方法处理树的拆分
                    // ===== 子情况3：桶中是普通链表 =====
                    else { // preserve order
                        // 优化：利用扩容时容量是2倍的特点
                        // 节点在新数组中的位置只有两种可能：
                        // 1. 保持原位置 j
                        // 2. 移动到 j + oldCap
                        // 判断依据：e.hash & oldCap
                        Node<K,V> loHead = null, loTail = null;// 低位链表（留在原位置）
                        Node<K,V> hiHead = null, hiTail = null;// 高位链表（移动到新位置）
                        Node<K,V> next; // 下一个节点临时变量
                        // 遍历链表，将节点分配到两个链表中，通过这里的do while循环会把整个链表的node重新串联起来生成两份完整的新链表
                        do {
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {// 如果(e.hash & oldCap) == 0
                                // 留在原位置（低位链表）
                                if (loTail == null)// 如果低位链表为空
                                    loHead = e;// e作为头节点
                                else
                                    loTail.next = e;// 连接到链表尾部
                                loTail = e;// 更新尾节点
                            }
                            else {// 如果(e.hash & oldCap) != 0
                                if (hiTail == null)// 如果高位链表为空
                                    hiHead = e;// e作为头节点
                                else
                                    hiTail.next = e;// 连接到链表尾部
                                hiTail = e;// 更新尾节点
                            }
                        } while ((e = next) != null);
                        // 将低位链表放到新数组的原位置j
                        if (loTail != null) {
                            loTail.next = null;// 确保链表结束
                            newTab[j] = loHead;// 设置到新数组
                        }
                        // 将高位链表放到新数组的新位置j+oldCap
                        if (hiTail != null) {
                            hiTail.next = null;// 确保链表结束
                            newTab[j + oldCap] = hiHead;// 新位置 = 原位置 + 旧容量
                        }
                    }
                }
            }
        }
        return newTab;// 返回新的扩容完毕且node重新分配的桶数组
    }

    /**
     * 将指定哈希值对应索引位置的单链表替换为双向树结构（红黑树）
     * 但如果哈希表太小（长度 < 64），则优先进行扩容而不是树化
     *
     * @param tab  哈希表数组
     * @param hash 要树化的元素的哈希值（用于定位桶索引）
     */
    final void treeifyBin(Node<K,V>[] tab, int hash) {
        int n, index;
        Node<K,V> e;

        // 情况1：哈希表为空或长度小于最小树化容量阈值（默认64）
        // 此时优先进行扩容，因为扩容后链表长度可能会变短
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
            resize();  // 扩容，而不是树化

            // 情况2：哈希表长度足够（>=64），且该桶位置有元素
        else if ((e = tab[index = (n - 1) & hash]) != null) {
            TreeNode<K,V> hd = null;  // 树化后的头节点（Tree的头）
            TreeNode<K,V> tl = null;  // 树化过程中的当前节点（用于构建双向链表）

            // 第一步：将普通Node链表转换为TreeNode双向链表
            do {
                // 将普通Node替换为TreeNode（此时还是链表结构）
                TreeNode<K,V> p = replacementTreeNode(e, null);

                if (tl == null)  // 第一个节点
                    hd = p;       // 设置为头节点
                else {            // 后续节点
                    p.prev = tl;   // 构建双向链表：设置前驱
                    tl.next = p;   // 构建双向链表：设置后继
                }
                tl = p;  // 移动当前指针到新节点
            } while ((e = e.next) != null);  // 遍历原链表

            // 第二步：将双向链表真正转换为红黑树
            if ((tab[index] = hd) != null)  // 将树化后的头节点放回桶中
                hd.treeify(tab);  // TreeNode的方法：将双向链表转换为红黑树
        }
    }

    /**
     * 将指定映射中的所有映射复制到该映射。这些映射将替换该映射中当前任意键的映射
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
    }

    /**
     * 如果有指定密钥的映射，则从该映射中移除
     */
    public V remove(Object key) {
        Node<K,V> e;
        return (e = removeNode(hash(key), key, null, false, true)) == null ?
                null : e.value;
    }

    /**
     * Implements Map.remove and related methods.
     * 实现 Map.remove 及相关方法的核心逻辑
     *
     * @param hash hash for key 键的哈希值
     * @param key the key 要移除的键
     * @param value the value to match if matchValue, else ignored
     *              当 matchValue 为 true 时需要匹配的值，否则忽略
     * @param matchValue if true only remove if value is equal
     *                   如果为 true，则仅在值相等时才移除
     * @param movable if false do not move other nodes while removing
     *                如果为 false，则在移除时不要移动其他节点（主要用于树的调整）
     * @return the node, or null if none 返回被移除的节点，如果没有找到则返回 null
     */
    final Node<K,V> removeNode(int hash, Object key, Object value,
                               boolean matchValue, boolean movable) {
        // tab: 当前哈希表的桶数组
        // p: 当前遍历的节点 / 目标桶的头节点
        // n: 桶数组的长度
        // index: 根据哈希值计算出的桶索引
        Node<K,V>[] tab; Node<K,V> p; int n, index;

        // 步骤1：检查桶数组是否为空且长度大于0，且目标桶位置不为空
        // 如果桶数组为空或目标桶为空，直接返回null
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (p = tab[index = (n - 1) & hash]) != null) {

            Node<K,V> node = null; // 用于存储找到的目标节点
            Node<K,V> e;           // 临时节点，用于遍历
            K k; V v;              // 临时变量，用于存储键值

            // 步骤2：检查头节点是否为要删除的节点
            // 比较头节点的哈希值和key是否匹配
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k)))) {
                node = p; // 头节点就是要找的节点
            }
            // 步骤3：头节点不是目标节点，且有下一个节点，继续查找
            else if ((e = p.next) != null) {
                // 步骤3.1：如果是树节点，调用红黑树的查找方法
                if (p instanceof TreeNode)
                    node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
                    // 步骤3.2：否则是链表结构，遍历链表查找
                else {
                    do {
                        // 检查当前节点是否匹配
                        if (e.hash == hash &&
                                ((k = e.key) == key ||
                                        (key != null && key.equals(k)))) {
                            node = e; // 找到目标节点
                            break;     // 结束循环
                        }
                        p = e; // p始终指向当前节点的前一个节点，用于后续的链表删除操作
                    } while ((e = e.next) != null); // 继续遍历下一个节点
                }
            }

            // 步骤4：如果找到了目标节点(node != null)，并且满足值匹配条件
            // matchValue为false时不校验值，为true时必须值也相等
            if (node != null && (!matchValue || (v = node.value) == value ||
                    (value != null && value.equals(v)))) {
                // 步骤4.1：根据节点类型执行不同的删除操作
                if (node instanceof TreeNode)
                    // 红黑树节点的删除（可能触发树的退化或调整）
                    ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
                else if (node == p)
                    // 删除的是头节点：直接将桶指向头节点的下一个节点
                    tab[index] = node.next;
                else
                    // 删除的是链表中的中间节点：
                    // 将前一个节点(p)的next指向被删除节点的下一个节点
                    p.next = node.next;

                // 步骤5：更新修改计数器、大小，并触发回调
                ++modCount;      // 修改计数器加1，用于fail-fast机制
                --size;          // 集合大小减1
                afterNodeRemoval(node); // LinkedHashMap的回调方法，用于维护双向链表

                // 返回被删除的节点
                return node;
            }
        }
        // 没有找到目标节点或不满足删除条件，返回null
        return null;
    }

    /**
     * 清空map
     */
    public void clear() {
        Node<K,V>[] tab;
        modCount++;// 修改计数器加1，用于fail-fast机制
        if ((tab = table) != null && size > 0) {// 如果桶数组不为空
            size = 0;// 全局size重置
            for (int i = 0; i < tab.length; ++i)// 遍历置空
                tab[i] = null;
        }
    }

    /**
     * 如果该映射将一个或多个键映射到指定值，则返回 tru
     */
    public boolean containsValue(Object value) {
        Node<K,V>[] tab; V v;
        if ((tab = table) != null && size > 0) {// 桶数组不为空
            for (Node<K,V> e : tab) {// 遍历桶数组
                for (; e != null; e = e.next) {// 遍历链表或树
                    if ((v = e.value) == value ||// 如果引用一致
                            (value != null && value.equals(v)))// 或者元素一样
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or {@code addAll}
     * operations.
     *
     * @return a set view of the keys contained in this map
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    /**
     * Prepares the array for {@link Collection#toArray(Object[])} implementation.
     * If supplied array is smaller than this map size, a new array is allocated.
     * If supplied array is bigger than this map size, a null is written at size index.
     *
     * @param a an original array passed to {@code toArray()} method
     * @param <T> type of array elements
     * @return an array ready to be filled and returned from {@code toArray()} method.
     */
    @SuppressWarnings("unchecked")
    final <T> T[] prepareArray(T[] a) {
        int size = this.size;
        if (a.length < size) {
            return (T[]) java.lang.reflect.Array
                    .newInstance(a.getClass().getComponentType(), size);
        }
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    /**
     * Fills an array with this map keys and returns it. This method assumes
     * that input array is big enough to fit all the keys. Use
     * {@link #prepareArray(Object[])} to ensure this.
     *
     * @param a an array to fill
     * @param <T> type of array elements
     * @return supplied array
     */
    <T> T[] keysToArray(T[] a) {
        Object[] r = a;
        Node<K,V>[] tab;
        int idx = 0;
        if (size > 0 && (tab = table) != null) {
            for (Node<K,V> e : tab) {
                for (; e != null; e = e.next) {
                    r[idx++] = e.key;
                }
            }
        }
        return a;
    }

    /**
     * Fills an array with this map values and returns it. This method assumes
     * that input array is big enough to fit all the values. Use
     * {@link #prepareArray(Object[])} to ensure this.
     *
     * @param a an array to fill
     * @param <T> type of array elements
     * @return supplied array
     */
    <T> T[] valuesToArray(T[] a) {
        Object[] r = a;
        Node<K,V>[] tab;
        int idx = 0;
        if (size > 0 && (tab = table) != null) {
            for (Node<K,V> e : tab) {
                for (; e != null; e = e.next) {
                    r[idx++] = e.value;
                }
            }
        }
        return a;
    }

    final class KeySet extends AbstractSet<K> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<K> iterator()     { return new KeyIterator(); }
        public final boolean contains(Object o) { return containsKey(o); }
        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }
        public final Spliterator<K> spliterator() {
            return new KeySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        public Object[] toArray() {
            return keysToArray(new Object[size]);
        }

        public <T> T[] toArray(T[] a) {
            return keysToArray(prepareArray(a));
        }

        public final void forEach(Consumer<? super K> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (Node<K,V> e : tab) {
                    for (; e != null; e = e.next)
                        action.accept(e.key);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll} and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     *
     * @return a view of the values contained in this map
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    final class Values extends AbstractCollection<V> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<V> iterator()     { return new ValueIterator(); }
        public final boolean contains(Object o) { return containsValue(o); }
        public final Spliterator<V> spliterator() {
            return new ValueSpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        public Object[] toArray() {
            return valuesToArray(new Object[size]);
        }

        public <T> T[] toArray(T[] a) {
            return valuesToArray(prepareArray(a));
        }

        public final void forEach(Consumer<? super V> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (Node<K,V> e : tab) {
                    for (; e != null; e = e.next)
                        action.accept(e.value);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation, or through the
     * {@code setValue} operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Set.remove}, {@code removeAll}, {@code retainAll} and
     * {@code clear} operations.  It does not support the
     * {@code add} or {@code addAll} operations.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e))
                return false;
            Object key = e.getKey();
            Node<K,V> candidate = getNode(key);
            return candidate != null && candidate.equals(e);
        }
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry<?, ?> e) {
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }
        public final Spliterator<Map.Entry<K,V>> spliterator() {
            return new EntrySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }
        public final void forEach(Consumer<? super Map.Entry<K,V>> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (Node<K,V> e : tab) {
                    for (; e != null; e = e.next)
                        action.accept(e);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    // Overrides of JDK8 Map extension methods

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        Node<K,V> e;
        return (e = getNode(key)) == null ? defaultValue : e.value;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true, true);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true, true) != null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Node<K,V> e; V v;
        if ((e = getNode(key)) != null &&
                ((v = e.value) == oldValue || (v != null && v.equals(oldValue)))) {
            e.value = newValue;
            afterNodeAccess(e);
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        Node<K,V> e;
        if ((e = getNode(key)) != null) {
            V oldValue = e.value;
            e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * mapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * mapping function modified this map
     */
    @Override
    public V computeIfAbsent(K key,
                             Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null)
            throw new NullPointerException();
        int hash = hash(key);
        Node<K,V>[] tab; Node<K,V> first; int n, i;
        int binCount = 0;
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        if (size > threshold || (tab = table) == null ||
                (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            else {
                Node<K,V> e = first; K k;
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
            V oldValue;
            if (old != null && (oldValue = old.value) != null) {
                afterNodeAccess(old);
                return oldValue;
            }
        }
        int mc = modCount;
        V v = mappingFunction.apply(key);
        if (mc != modCount) { throw new ConcurrentModificationException(); }
        if (v == null) {
            return null;
        } else if (old != null) {
            old.value = v;
            afterNodeAccess(old);
            return v;
        }
        else if (t != null)
            t.putTreeVal(this, tab, hash, key, v);
        else {
            tab[i] = newNode(hash, key, v, first);
            if (binCount >= TREEIFY_THRESHOLD - 1)
                treeifyBin(tab, hash);
        }
        modCount = mc + 1;
        ++size;
        afterNodeInsertion(true);
        return v;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * remapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * remapping function modified this map
     */
    @Override
    public V computeIfPresent(K key,
                              BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        Node<K,V> e; V oldValue;
        if ((e = getNode(key)) != null &&
                (oldValue = e.value) != null) {
            int mc = modCount;
            V v = remappingFunction.apply(key, oldValue);
            if (mc != modCount) { throw new ConcurrentModificationException(); }
            if (v != null) {
                e.value = v;
                afterNodeAccess(e);
                return v;
            }
            else {
                int hash = hash(key);
                removeNode(hash, key, null, false, true);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * remapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * remapping function modified this map
     */
    @Override
    public V compute(K key,
                     BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        int hash = hash(key);
        Node<K,V>[] tab; Node<K,V> first; int n, i;
        int binCount = 0;
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        if (size > threshold || (tab = table) == null ||
                (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            else {
                Node<K,V> e = first; K k;
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        V oldValue = (old == null) ? null : old.value;
        int mc = modCount;
        V v = remappingFunction.apply(key, oldValue);
        if (mc != modCount) { throw new ConcurrentModificationException(); }
        if (old != null) {
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            }
            else
                removeNode(hash, key, null, false, true);
        }
        else if (v != null) {
            if (t != null)
                t.putTreeVal(this, tab, hash, key, v);
            else {
                tab[i] = newNode(hash, key, v, first);
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    treeifyBin(tab, hash);
            }
            modCount = mc + 1;
            ++size;
            afterNodeInsertion(true);
        }
        return v;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * remapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * remapping function modified this map
     */
    @Override
    public V merge(K key, V value,
                   BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (value == null || remappingFunction == null)
            throw new NullPointerException();
        int hash = hash(key);
        Node<K,V>[] tab; Node<K,V> first; int n, i;
        int binCount = 0;
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        if (size > threshold || (tab = table) == null ||
                (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            else {
                Node<K,V> e = first; K k;
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        if (old != null) {
            V v;
            if (old.value != null) {
                int mc = modCount;
                v = remappingFunction.apply(old.value, value);
                if (mc != modCount) {
                    throw new ConcurrentModificationException();
                }
            } else {
                v = value;
            }
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            }
            else
                removeNode(hash, key, null, false, true);
            return v;
        } else {
            if (t != null)
                t.putTreeVal(this, tab, hash, key, value);
            else {
                tab[i] = newNode(hash, key, value, first);
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    treeifyBin(tab, hash);
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
            return value;
        }
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Node<K,V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (Node<K,V> e : tab) {
                for (; e != null; e = e.next)
                    action.accept(e.key, e.value);
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Node<K,V>[] tab;
        if (function == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (Node<K,V> e : tab) {
                for (; e != null; e = e.next) {
                    e.value = function.apply(e.key, e.value);
                }
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    /* ------------------------------------------------------------ */
    // Cloning and serialization

    /**
     * Returns a shallow copy of this {@code HashMap} instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        HashMap<K,V> result;
        try {
            result = (HashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    // These methods are also used when serializing HashSets
    final float loadFactor() { return loadFactor; }
    final int capacity() {
        return (table != null) ? table.length :
                (threshold > 0) ? threshold :
                        DEFAULT_INITIAL_CAPACITY;
    }

    /**
     * Saves this map to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws IOException if an I/O error occurs
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     *             bucket array) is emitted (int), followed by the
     *             <i>size</i> (an int, the number of key-value
     *             mappings), followed by the key (Object) and value (Object)
     *             for each key-value mapping.  The key-value mappings are
     *             emitted in no particular order.
     */
    @java.io.Serial
    private void writeObject(java.io.ObjectOutputStream s)
            throws IOException {
        int buckets = capacity();
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(size);
        internalWriteEntries(s);
    }

    /**
     * Reconstitutes this map from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws IOException if an I/O error occurs
     */
    @java.io.Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {

        ObjectInputStream.GetField fields = s.readFields();

        // Read loadFactor (ignore threshold)
        float lf = fields.get("loadFactor", 0.75f);
        if (lf <= 0 || Float.isNaN(lf))
            throw new InvalidObjectException("Illegal load factor: " + lf);

        lf = Math.min(Math.max(0.25f, lf), 4.0f);
        HashMap.UnsafeHolder.putLoadFactor(this, lf);

        reinitialize();

        s.readInt();                // Read and ignore number of buckets
        int mappings = s.readInt(); // Read number of mappings (size)
        if (mappings < 0) {
            throw new InvalidObjectException("Illegal mappings count: " + mappings);
        } else if (mappings == 0) {
            // use defaults
        } else if (mappings > 0) {
            float fc = (float)mappings / lf + 1.0f;
            int cap = ((fc < DEFAULT_INITIAL_CAPACITY) ?
                    DEFAULT_INITIAL_CAPACITY :
                    (fc >= MAXIMUM_CAPACITY) ?
                            MAXIMUM_CAPACITY :
                            tableSizeFor((int)fc));
            float ft = (float)cap * lf;
            threshold = ((cap < MAXIMUM_CAPACITY && ft < MAXIMUM_CAPACITY) ?
                    (int)ft : Integer.MAX_VALUE);

            // Check Map.Entry[].class since it's the nearest public type to
            // what we're actually creating.
            SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Map.Entry[].class, cap);
            @SuppressWarnings({"rawtypes","unchecked"})
            Node<K,V>[] tab = (Node<K,V>[])new Node[cap];
            table = tab;

            // Read the keys and values, and put the mappings in the HashMap
            for (int i = 0; i < mappings; i++) {
                @SuppressWarnings("unchecked")
                K key = (K) s.readObject();
                @SuppressWarnings("unchecked")
                V value = (V) s.readObject();
                putVal(hash(key), key, value, false, false);
            }
        }
    }

    // Support for resetting final field during deserializing
    private static final class UnsafeHolder {
        private UnsafeHolder() { throw new InternalError(); }
        private static final jdk.internal.misc.Unsafe unsafe
                = jdk.internal.misc.Unsafe.getUnsafe();
        private static final long LF_OFFSET
                = unsafe.objectFieldOffset(HashMap.class, "loadFactor");
        static void putLoadFactor(HashMap<?, ?> map, float lf) {
            unsafe.putFloat(map, LF_OFFSET, lf);
        }
    }

    /* ------------------------------------------------------------ */
    // iterators

    abstract class HashIterator {
        Node<K,V> next;        // next entry to return
        Node<K,V> current;     // current entry
        int expectedModCount;  // for fast-fail
        int index;             // current slot

        HashIterator() {
            expectedModCount = modCount;
            Node<K,V>[] t = table;
            current = next = null;
            index = 0;
            if (t != null && size > 0) { // advance to first entry
                do {} while (index < t.length && (next = t[index++]) == null);
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Node<K,V> nextNode() {
            Node<K,V>[] t;
            Node<K,V> e = next;
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (e == null)
                throw new NoSuchElementException();
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {} while (index < t.length && (next = t[index++]) == null);
            }
            return e;
        }

        public final void remove() {
            Node<K,V> p = current;
            if (p == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            current = null;
            removeNode(p.hash, p.key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class KeyIterator extends HashIterator
            implements Iterator<K> {
        public final K next() { return nextNode().key; }
    }

    final class ValueIterator extends HashIterator
            implements Iterator<V> {
        public final V next() { return nextNode().value; }
    }

    final class EntryIterator extends HashIterator
            implements Iterator<Map.Entry<K,V>> {
        public final Map.Entry<K,V> next() { return nextNode(); }
    }

    /* ------------------------------------------------------------ */
    // spliterators

    static class HashMapSpliterator<K,V> {
        final HashMap<K,V> map;
        Node<K,V> current;          // current node
        int index;                  // current index, modified on advance/split
        int fence;                  // one past last index
        int est;                    // size estimate
        int expectedModCount;       // for comodification checks

        HashMapSpliterator(HashMap<K,V> m, int origin,
                           int fence, int est,
                           int expectedModCount) {
            this.map = m;
            this.index = origin;
            this.fence = fence;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getFence() { // initialize fence and size on first use
            int hi;
            if ((hi = fence) < 0) {
                HashMap<K,V> m = map;
                est = m.size;
                expectedModCount = m.modCount;
                Node<K,V>[] tab = m.table;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            return hi;
        }

        public final long estimateSize() {
            getFence(); // force init
            return (long) est;
        }
    }

    static final class KeySpliterator<K,V>
            extends HashMapSpliterator<K,V>
            implements Spliterator<K> {
        KeySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                       int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public KeySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new KeySpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super K> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.key);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        K k = current.key;
                        current = current.next;
                        action.accept(k);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT;
        }
    }

    static final class ValueSpliterator<K,V>
            extends HashMapSpliterator<K,V>
            implements Spliterator<V> {
        ValueSpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public ValueSpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new ValueSpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super V> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.value);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        V v = current.value;
                        current = current.next;
                        action.accept(v);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0);
        }
    }

    static final class EntrySpliterator<K,V>
            extends HashMapSpliterator<K,V>
            implements Spliterator<Map.Entry<K,V>> {
        EntrySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public EntrySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new EntrySpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K,V>> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        Node<K,V> e = current;
                        current = current.next;
                        action.accept(e);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT;
        }
    }

    /* ------------------------------------------------------------ */
    // LinkedHashMap support


    /*
     * The following package-protected methods are designed to be
     * overridden by LinkedHashMap, but not by any other subclass.
     * Nearly all other internal methods are also package-protected
     * but are declared final, so can be used by LinkedHashMap, view
     * classes, and HashSet.
     */

    // Create a regular (non-tree) node
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
        return new Node<>(hash, key, value, next);
    }

    // For conversion from TreeNodes to plain nodes
    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        return new Node<>(p.hash, p.key, p.value, next);
    }

    // Create a tree bin node
    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        return new TreeNode<>(hash, key, value, next);
    }

    // For treeifyBin
    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        return new TreeNode<>(p.hash, p.key, p.value, next);
    }

    /**
     * Reset to initial default state.  Called by clone and readObject.
     */
    void reinitialize() {
        table = null;
        entrySet = null;
        keySet = null;
        values = null;
        modCount = 0;
        threshold = 0;
        size = 0;
    }

    // Callbacks to allow LinkedHashMap post-actions
    void afterNodeAccess(Node<K,V> p) { }
    void afterNodeInsertion(boolean evict) { }
    void afterNodeRemoval(Node<K,V> p) { }

    // Called only from writeObject, to ensure compatible ordering.
    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        Node<K,V>[] tab;
        if (size > 0 && (tab = table) != null) {
            for (Node<K,V> e : tab) {
                for (; e != null; e = e.next) {
                    s.writeObject(e.key);
                    s.writeObject(e.value);
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
    // Tree bins

    /**
     * TreeNode 既可以用作红黑树的节点（有 parent， left， right， red 属性），也可以用作普通链表的节点（通过 next 属性）。
     * 这种双重身份是为了在扩容或反树化时，能够方便地在树和链表之间转换
     */
    static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
        TreeNode<K,V> parent;  // 红黑树的父节点
        TreeNode<K,V> left;    // 左子节点
        TreeNode<K,V> right;   // 右子节点
        TreeNode<K,V> prev;    // 前一个节点（链表顺序的前驱，主要用于删除时维护链表）
        boolean red;           // 红黑树的颜色标记（true=红色，false=黑色）
        TreeNode(int hash, K key, V val, Node<K,V> next) {
            super(hash, key, val, next);
        }

        /**
         * 返回包含当前节点的树的根节点
         */
        final TreeNode<K,V> root() {
            // 从当前节点开始向上遍历parent链，直到找到根节点（parent为null的节点）
            for (TreeNode<K,V> r = this, p;;) {// r初始化为当前节点，无限循环
                if ((p = r.parent) == null) // 将r的parent赋值给p，并判断是否为null
                    return r;// 如果parent为null，说明r就是根节点，返回r
                r = p;// 否则将r指向其父节点，继续向上查找
            }
        }

        /**
         * 确保给定的根节点是其桶（数组槽位）的第一个节点。
         *
         * @param tab HashMap的底层数组
         * @param root 要移动到前端的根节点
         */
        static <K,V> void moveRootToFront(Node<K,V>[] tab, TreeNode<K,V> root) {
            int n; // 声明数组长度变量
            if (root != null && tab != null && (n = tab.length) > 0) { // 检查参数有效性：root不为null，tab不为null，tab长度大于0
                int index = (n - 1) & root.hash; // 根据root的hash值计算该节点在数组中的索引位置
                TreeNode<K,V> first = (TreeNode<K,V>)tab[index];// 获取桶数组的头节点
                if (root != first) {// 如果root不是头节点，才需要进行移动操作
                    Node<K,V> rn;// 声明root的next节点
                    tab[index] = root;// 将root置为桶数组的头节点
                    TreeNode<K,V> rp = root.prev;// 获取root的前驱节点
                    if ((rn = root.next) != null)// 赋值root的后继节点并且不为空
                        ((TreeNode<K,V>)rn).prev = rp;// 将root.next的prev指针指向root.prev，跳过root节点
                    if (rp != null)// 如果root.prev不为null
                        rp.next = rn;// 将root.prev的next指针指向root.next，跳过root节点
                    if (first != null)// 如果原来的头节点first不为null
                        first.prev = root;// 将first的prev指针指向root，因为root要插入到first前面
                    root.next = first;// 将root的next指针指向原来的头节点first
                    root.prev = null;// 将root的prev指针设为null，因为root现在是头节点，没有前驱
                }
                assert checkInvariants(root);// 递归检查链表正确性和红黑树正确性
            }
        }

        /**
         * 从根节点p开始，查找具有给定hash和key的节点。
         * kc参数用于缓存key的Comparable类型，第一次比较键时设置。
         *
         * @param h 要查找的hash值
         * @param k 要查找的key
         * @param kc key的Comparable类型（缓存，避免重复计算）
         * @return 找到的节点，如果没找到返回null
         */
        final TreeNode<K,V> find(int h, Object k, Class<?> kc) {
            TreeNode<K,V> p = this;// p初始化为当前节点（查找的起点）
            do {// do-while循环，至少执行一次
                int ph, dir; K pk;// 声明变量：ph-当前节点hash，dir-比较方向，pk-当前节点key
                TreeNode<K,V> pl = p.left, pr = p.right, q;// pl-左子节点，pr-右子节点，q-临时变量
                if ((ph = p.hash) > h)// 如果当前节点hash大于目标hash，向左子树查找
                    p = pl;// 应该向左子树查找（因为左子树hash小）
                else if (ph < h)// 如果当前节点hash小于目标hash，向右子树查找
                    p = pr;// 应该向右子树查找（因为右子树hash大）
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))// 如果当前节点key等于目标key
                    return p;// 返回当前节点

                // 走到这里说明hash相等但key不相等，需要继续在子树中查找
                else if (pl == null)// 如果左子节点为空
                    p = pr;// 只能向右子树查找
                else if (pr == null)// 如果右子节点为空
                    p = pl;// 只能向左子树查找

                // 到这里说明左右子树都不为null，且key不相等，需要进一步判断查找方向
                else if ((kc != null || // 如果kc不为null（有缓存的Comparable类型）或
                        (kc = comparableClassFor(k)) != null) && // 能获取到key的Comparable类型
                        (dir = compareComparables(kc, k, pk)) != 0) // 且compareTo比较结果不为0
                    p = (dir < 0) ? pl : pr; // 根据compareTo结果决定方向（负数向左，正数向右）

                // 如果compareTo也无法决定方向（或者key没有实现Comparable），就在右子树中递归查找
                else if ((q = pr.find(h, k, kc)) != null) // 在右子树中递归查找
                    return q; // 如果在右子树中找到，直接返回
                else
                    p = pl; // 右子树没找到，去左子树继续查找
            } while (p != null); // 当p不为null时继续循环
            return null;// 没找到，返回null
        }

        /**
         * 调用根节点的find方法进行查找。
         *
         * @param h 要查找的hash值
         * @param k 要查找的key
         * @return 找到的节点，如果没找到返回null
         */
        final TreeNode<K,V> getTreeNode(int h, Object k) {
            // parent != null 说明不是根节点，先找到根节点然后调用find查找目标对象
            return ((parent != null) ? root() : this).find(h, k, null);
        }

        /**
         * 当hashCode相等且key不可比较时，用于决定插入顺序的决胜工具。
         * 不需要全序，只需要一个一致的插入规则来保持重平衡时的等价性。
         * 适度的决胜规则也简化了测试。
         *
         * @param a 第一个对象
         * @param b 第二个对象
         * @return 比较结果，负数表示a<b，正数表示a>b
         */
        static int tieBreakOrder(Object a, Object b) {
            int d; // 声明比较结果变量
            // 如果a或b为null，或者通过类名比较的结果为0（相等）
            if (a == null || b == null ||
                    (d = a.getClass().getName().
                            compareTo(b.getClass().getName())) == 0)
                // 使用系统的identityHashCode（基于对象内存地址的hashCode）进行比较
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
                        -1 : 1);// 如果a的identityHashCode <= b的，返回-1，否则返回1
            return d;// 返回最终比较结果
        }

        /**
         * 将当前节点链接的节点链表转换为一棵红黑树。
         *
         * @param tab HashMap的底层数组
         */
        final void treeify(Node<K,V>[] tab) {
            TreeNode<K,V> root = null;// 初始化根节点为null
            // 遍历链表：x从当前节点开始，每次循环后x指向next节点
            for (TreeNode<K,V> x = this, next; x != null; x = next) {
                next = (TreeNode<K,V>)x.next;// 保存下一个节点
                x.left = x.right = null;// 清空当前节点的左右子节点
                if (root == null) {// 如果根节点还没设置（第一个节点）
                    x.parent = null;// 根节点没有父节点
                    x.red = false;// 根节点设置为黑节点
                    root = x;// 将当前节点设置为根节点
                }
                else {// 已经有根节点了，需要将当前节点插入到树中
                    K k = x.key;// 获取当前节点的key
                    int h = x.hash;// 获取当前节点的hash
                    Class<?> kc = null;// 初始化key的Comparable类型缓存

                    // 从根节点开始，找到当前节点应该插入的位置
                    for (TreeNode<K,V> p = root;;) {
                        int dir, ph;// dir-比较方向，ph-当前比较节点的hash
                        K pk = p.key;// 当前比较节点的key
                        if ((ph = p.hash) > h)// 如果当前比较节点的hash大于插入节点的hash
                            dir = -1;// 应该向左子树插入（dir为-1）
                        else if (ph < h)// 如果当前比较节点的hash小于插入节点的hash
                            dir = 1;// 应该向右子树插入（dir为1）

                        // hash相等的情况，需要进一步比较
                        else if ((kc == null && // 如果还没有缓存Comparable类型且
                                (kc = comparableClassFor(k)) == null) || // 获取不到Comparable类型或
                                (dir = compareComparables(kc, k, pk)) == 0) // compareTo结果为0
                            dir = tieBreakOrder(k, pk);// 使用决胜规则决定方向

                        TreeNode<K,V> xp = p; // 保存当前比较节点（即将成为插入节点的父节点）
                        // 根据dir方向决定去左子树还是右子树，如果子树为null，说明找到了插入位置
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            x.parent = xp;// 设置插入节点的父节点
                            if (dir <= 0)// 如果dir为-1，说明应该插入到左子树
                                xp.left = x;// 插入到左子树
                            else// 如果dir为1，说明应该插入到右子树
                                xp.right = x;// 插入到右子树
                            root = balanceInsertion(root, x);// 插入后需要平衡红黑树（可能需要进行旋转和变色）
                            break; // 插入完成，退出内层循环
                        }
                    }
                }
            }
            // 确保树的根节点在数组的对应桶中处于第一个位置
            moveRootToFront(tab, root);
        }

        /**
         * 将红黑树节点链表转换回普通的单向链表节点
         * 当树中节点数量减少到阈值以下（<= 6）时调用
         *
         * @param map HashMap实例（用于创建替换节点）
         * @return 新的链表头节点
         */
        final Node<K,V> untreeify(HashMap<K,V> map) {
            // hd (head)：新链表的头节点
            // tl (tail)：新链表的尾节点（用于在遍历过程中追加新节点）
            Node<K,V> hd = null, tl = null;

            // 遍历当前节点（this）及其next指针构成的链表
            // q从当前节点开始，每次循环后指向q.next，直到q为null
            for (Node<K,V> q = this; q != null; q = q.next) {

                // 关键步骤：将树节点q替换为普通的Node节点
                // replacementNode方法会创建一个新的Node对象，包含相同的hash、key、value
                // 第二个参数null表示新节点的next指针暂时为null
                Node<K,V> p = map.replacementNode(q, null);

                if (tl == null)          // 如果是第一个节点
                    hd = p;               // 将头节点指向p
                else                      // 如果不是第一个节点
                    tl.next = p;          // 将上一个节点的next指向当前节点（构建链表）

                tl = p;                   // 更新尾节点为当前节点
            }

            return hd;  // 返回新链表的头节点
        }

        /**
         * 树版本的 putVal 方法
         * 向红黑树中插入一个键值对，如果键已存在则返回该节点
         *
         * @param map HashMap实例
         * @param tab 底层数组
         * @param h 键的hash值
         * @param k 键
         * @param v 值
         * @return 如果存在相同的key，返回已存在的节点；否则返回null
         */
        final TreeNode<K,V> putTreeVal(HashMap<K,V> map, Node<K,V>[] tab,
                                       int h, K k, V v) {
            // kc：用于缓存key的Comparable类型，避免重复计算
            Class<?> kc = null;
            // searched：标记是否已经在子树中搜索过该key（避免重复搜索）
            boolean searched = false;

            // 获取根节点：如果当前节点不是根节点，就找到根节点；否则当前节点就是根节点
            TreeNode<K,V> root = (parent != null) ? root() : this;

            // 从根节点开始循环查找插入位置
            for (TreeNode<K,V> p = root;;) {
                int dir, ph;
                K pk;

                // ===== 第1步：比较hash值，决定向左还是向右 =====
                if ((ph = p.hash) > h)
                    dir = -1;           // 当前节点hash > 插入hash，向左走
                else if (ph < h)
                    dir = 1;            // 当前节点hash < 插入hash，向右走

                    // ===== 第2步：hash相等，比较key是否相等 =====
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                    return p;           // key相同，找到已存在的节点，直接返回

                    // ===== 第3步：hash相等但key不同，需要进一步比较 =====
                else if ((kc == null &&
                        (kc = comparableClassFor(k)) == null) ||
                        (dir = compareComparables(kc, k, pk)) == 0) {

                    // 如果还没有搜索过子树
                    if (!searched) {
                        TreeNode<K,V> q, ch;
                        searched = true;  // 标记已搜索

                        // 分别在左右子树中递归查找该key
                        // 如果左子树不为空且在左子树中找到，或者右子树不为空且在右子树中找到
                        if (((ch = p.left) != null &&
                                (q = ch.find(h, k, kc)) != null) ||
                                ((ch = p.right) != null &&
                                        (q = ch.find(h, k, kc)) != null))
                            return q;  // 在子树中找到，返回该节点
                    }

                    // 子树中也找不到，使用决胜规则决定方向
                    // tieBreakOrder会通过类名或identityHashCode决定比较结果
                    dir = tieBreakOrder(k, pk);
                }

                // ===== 第4步：根据方向移动到子节点，如果为null则插入 =====
                TreeNode<K,V> xp = p;  // xp保存当前节点，将成为新节点的父节点

                // 根据dir方向移动到相应的子节点
                // 如果子节点为null，说明找到了插入位置
                if ((p = (dir <= 0) ? p.left : p.right) == null) {

                    // 获取父节点的next（用于维护链表结构）
                    Node<K,V> xpn = xp.next;

                    // 创建新的树节点
                    // newTreeNode方法会创建一个TreeNode对象，包含hash、key、value，并设置next为xpn
                    TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn);

                    // ===== 维护红黑树结构 =====
                    if (dir <= 0)        // 如果是向左插入
                        xp.left = x;     // 将新节点设为父节点的左子节点
                    else                 // 如果是向右插入
                        xp.right = x;    // 将新节点设为父节点的右子节点

                    // ===== 维护双向链表结构 =====
                    xp.next = x;         // 父节点的next指向新节点
                    x.parent = x.prev = xp;  // 新节点的parent和prev都指向父节点

                    if (xpn != null)      // 如果原父节点的next不为空
                        ((TreeNode<K,V>)xpn).prev = x;  // 将原next节点的prev指向新节点（形成双向链表）

                    // ===== 插入后平衡 + 确保根节点在桶的第一个位置 =====
                    // balanceInsertion：插入后红黑树平衡调整
                    // moveRootToFront：确保根节点在数组桶中处于第一个位置
                    moveRootToFront(tab, balanceInsertion(root, x));

                    return null;  // 返回null表示插入成功，没有已存在的key
                }
            }
        }

        /**
         * 删除给定的节点（该节点必须在调用前存在于树中）
         *
         * 这个方法比标准的红黑树删除更复杂，因为不能简单地将内部节点的内容
         * 与后继叶子节点交换，因为"next"指针在遍历期间是独立可访问的。
         * 所以改为交换树链接。如果当前树的节点太少，桶会被转换回普通链表。
         * （根据树结构的不同，触发转换的节点数在2到6之间）
         *
         * @param map HashMap实例
         * @param tab 底层数组
         * @param movable 是否允许移动根节点到数组桶的第一个位置
         */
        final void removeTreeNode(HashMap<K,V> map, Node<K,V>[] tab,
                                  boolean movable) {
            int n;  // 声明数组长度变量

            // ===== 第1步：基础检查 =====
            // 如果数组为空或长度为0，直接返回
            if (tab == null || (n = tab.length) == 0)
                return;

            // 根据当前节点的hash值计算它在数组中的索引位置
            int index = (n - 1) & hash;

            // first：该索引位置的头节点
            // root：根节点（初始设为first）
            TreeNode<K,V> first = (TreeNode<K,V>)tab[index], root = first, rl;

            // succ：当前节点的下一个节点（链表的后继）
            // pred：当前节点的上一个节点（链表的前驱）
            TreeNode<K,V> succ = (TreeNode<K,V>)next, pred = prev;

            // ===== 第2步：从双向链表中删除当前节点 =====
            // 这部分维护的是通过next/prev连接的双向链表结构

            if (pred == null)  // 如果当前节点没有前驱（即它是头节点）
                tab[index] = first = succ;  // 将头节点指向它的下一个节点
            else  // 如果当前节点有前驱
                pred.next = succ;  // 将前驱节点的next指向当前节点的下一个节点（跳过当前节点）

            if (succ != null)  // 如果当前节点有后继
                succ.prev = pred;  // 将后继节点的prev指向前驱节点（双向链表维护完成）

            if (first == null)  // 如果删除后头节点为null（链表空了）
                return;          // 直接返回

            // ===== 第3步：确保root是真正的根节点 =====
            if (root.parent != null)
                root = root.root();

            // ===== 第4步：判断是否需要将树转换回链表 =====
            // 条件：节点太少（根据红黑树结构判断，大致在2-6个节点时触发）
            if (root == null
                    || (movable  // 如果允许移动
                    && (root.right == null  // 根节点的右子树为null
                    || (rl = root.left) == null  // 根节点的左子树为null
                    || rl.left == null))) {  // 左子树的左子树为null（整棵树非常小）
                tab[index] = first.untreeify(map);  // 转换为普通链表
                return;  // 转换完成，直接返回
            }

            // ===== 第5步：开始红黑树的删除操作 =====
            // p：当前要删除的节点（this）
            // pl：p的左子节点
            // pr：p的右子节点
            // replacement：用于替换p的节点
            TreeNode<K,V> p = this, pl = left, pr = right, replacement;

            // ===== 情况1：p有左右两个子节点（最复杂的情况） =====
            if (pl != null && pr != null) {
                TreeNode<K,V> s = pr, sl;  // s初始化为右子节点，用于寻找后继节点

                // 寻找后继节点：右子树的最左边节点
                // 后继节点是大于p的最小节点
                while ((sl = s.left) != null)  // 一直向左找到最左边的节点
                    s = sl;

                // 交换p和s的颜色
                boolean c = s.red;
                s.red = p.red;
                p.red = c;

                TreeNode<K,V> sr = s.right;  // s的右子节点
                TreeNode<K,V> pp = p.parent;  // p的父节点

                // ===== 子情况1.1：s是p的直接右子节点 =====
                if (s == pr) {  // p was s's direct parent
                    p.parent = s;  // p的父节点设为s
                    s.right = p;   // s的右子节点设为p
                }
                else {  // ===== 子情况1.2：s不是p的直接右子节点 =====
                    TreeNode<K,V> sp = s.parent;  // s的父节点

                    // 将p的父节点设为s的父节点
                    if ((p.parent = sp) != null) {
                        if (s == sp.left)  // 如果s是sp的左子节点
                            sp.left = p;   // 将sp的左子节点设为p
                        else                // 如果s是sp的右子节点
                            sp.right = p;  // 将sp的右子节点设为p
                    }

                    // 将s的右子节点设为p的原右子节点
                    if ((s.right = pr) != null)
                        pr.parent = s;  // 更新pr的父节点
                }

                // 清空p的左子节点引用
                p.left = null;

                // 将p的右子节点设为s的原右子节点
                if ((p.right = sr) != null)
                    sr.parent = p;  // 更新sr的父节点

                // 将s的左子节点设为p的原左子节点
                if ((s.left = pl) != null)
                    pl.parent = s;  // 更新pl的父节点

                // 将s的父节点设为p的原父节点
                if ((s.parent = pp) == null)
                    root = s;  // 如果pp为null，s成为新的根节点
                else if (p == pp.left)  // 如果p原来是pp的左子节点
                    pp.left = s;  // 将pp的左子节点设为s
                else  // 如果p原来是pp的右子节点
                    pp.right = s;  // 将pp的右子节点设为s

                // 确定替换节点
                if (sr != null)
                    replacement = sr;  // 如果有右子节点，用它替换
                else
                    replacement = p;    // 否则用p本身替换（p会向上移动）
            }
            // ===== 情况2：只有左子节点 =====
            else if (pl != null)
                replacement = pl;
                // ===== 情况3：只有右子节点 =====
            else if (pr != null)
                replacement = pr;
                // ===== 情况4：没有子节点（叶子节点） =====
            else
                replacement = p;

            // ===== 第6步：执行实际的替换操作 =====
            if (replacement != p) {  // 如果替换节点不是p本身
                TreeNode<K,V> pp = replacement.parent = p.parent;  // 设置替换节点的父节点

                if (pp == null)  // 如果p是根节点
                    (root = replacement).red = false;  // 新根节点设为黑色
                else if (p == pp.left)  // 如果p是pp的左子节点
                    pp.left = replacement;  // 将pp的左子节点指向替换节点
                else  // 如果p是pp的右子节点
                    pp.right = replacement;  // 将pp的右子节点指向替换节点

                // 清空p的所有引用（帮助GC）
                p.left = p.right = p.parent = null;
            }

            // ===== 第7步：平衡红黑树 =====
            // 如果p是红色，删除不会影响黑高，不需要平衡
            // 如果p是黑色，需要调用balanceDeletion恢复平衡
            TreeNode<K,V> r = p.red ? root : balanceDeletion(root, replacement);

            // ===== 第8步：处理替换节点就是p本身的情况（叶子节点） =====
            if (replacement == p) {  // detach
                TreeNode<K,V> pp = p.parent;  // 获取p的父节点
                p.parent = null;  // 清空p的父节点引用

                if (pp != null) {  // 如果父节点存在
                    if (p == pp.left)  // 如果p是左子节点
                        pp.left = null;  // 将父节点的左子节点设为null
                    else if (p == pp.right)  // 如果p是右子节点
                        pp.right = null;  // 将父节点的右子节点设为null
                }
            }

            // ===== 第9步：如果需要，将根节点移动到数组桶的第一个位置 =====
            if (movable)
                moveRootToFront(tab, r);
        }
        /**
         * 将树桶中的节点拆分为低位树桶和高位树桶，
         * 如果节点太少则反树化。只在resize扩容时调用。
         *
         * @param map HashMap实例
         * @param tab 新数组（用于记录桶的头节点）
         * @param index 正在拆分的旧数组索引
         * @param bit 用于拆分的hash位（通常是旧数组容量，即扩容后新增的那一位）
         */
        final void split(HashMap<K,V> map, Node<K,V>[] tab, int index, int bit) {
            TreeNode<K,V> b = this;  // b是当前树节点（当前桶的头节点）

            // ===== 第1步：将树拆分为两个链表 =====
            // loHead/loTail：低位链表（索引不变）的头和尾
            // hiHead/hiTail：高位链表（索引+旧容量）的头和尾
            // lc/hc：低位和高位链表的节点计数器
            TreeNode<K,V> loHead = null, loTail = null;
            TreeNode<K,V> hiHead = null, hiTail = null;
            int lc = 0, hc = 0;

            // 遍历整棵树（同时也是遍历双向链表）
            for (TreeNode<K,V> e = b, next; e != null; e = next) {
                next = (TreeNode<K,V>)e.next;  // 保存下一个节点
                e.next = null;  // 清空当前节点的next，准备重新链接

                // 关键判断：e.hash & bit == 0 决定节点留在原位还是移动到高位
                // bit是旧数组容量，比如16（二进制10000）
                // 这个操作检查hash值在新增的那一位上是0还是1
                if ((e.hash & bit) == 0) {  // 结果为0，留在低位（原索引）
                    if ((e.prev = loTail) == null)  // 如果是低位链表的第一个节点
                        loHead = e;  // 设为头节点
                    else
                        loTail.next = e;  // 否则加到链表尾部
                    loTail = e;  // 更新尾节点
                    ++lc;  // 低位节点计数+1
                }
                else {  // 结果不为0，移动到高位（原索引 + 旧容量）
                    if ((e.prev = hiTail) == null)  // 如果是高位链表的第一个节点
                        hiHead = e;  // 设为头节点
                    else
                        hiTail.next = e;  // 否则加到链表尾部
                    hiTail = e;  // 更新尾节点
                    ++hc;  // 高位节点计数+1
                }
            }

            // ===== 第2步：处理低位链表 =====
            if (loHead != null) {
                if (lc <= UNTREEIFY_THRESHOLD)  // 如果节点数 <= 6
                    tab[index] = loHead.untreeify(map);  // 反树化，变成普通链表
                else {
                    tab[index] = loHead;  // 否则将低位链表放回原索引位置
                    if (hiHead != null)  // 如果高位链表不为空（说明有节点移动）
                        loHead.treeify(tab);  // 重新树化（因为树被拆分了，结构可能变化）
                }
            }

            // ===== 第3步：处理高位链表 =====
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD)  // 如果节点数 <= 6
                    tab[index + bit] = hiHead.untreeify(map);  // 反树化
                else {
                    tab[index + bit] = hiHead;  // 放入高位索引位置（原索引 + 旧容量）
                    if (loHead != null)  // 如果低位链表不为空
                        hiHead.treeify(tab);  // 重新树化
                }
            }
        }

        /**
         * 红黑树左旋操作
         * 将节点p向右下方旋转，让它的右子节点r旋转到p的位置
         *
         * 左旋前：      左旋后：
         *     p           r
         *    / \         / \
         *   a   r   →   p   c
         *      / \     / \
         *     b   c   a   b
         *
         * @param root 当前根节点
         * @param p 要旋转的节点
         * @return 新的根节点（可能变化）
         */
        static <K,V> TreeNode<K,V> rotateLeft(TreeNode<K,V> root,
                                              TreeNode<K,V> p) {
            TreeNode<K,V> r, pp, rl;  // r: p的右子节点, pp: p的父节点, rl: r的左子节点

            // 检查是否可以左旋：p存在且p的右子节点r存在
            if (p != null && (r = p.right) != null) {

                // ===== 第1步：处理r的左子节点 =====
                // 将r的左子节点(rl)移给p作为右子节点
                if ((rl = p.right = r.left) != null)
                    rl.parent = p;  // 如果rl存在，更新它的父节点为p

                // ===== 第2步：处理p的父节点 =====
                // 将r的父节点设为p的父节点
                if ((pp = r.parent = p.parent) == null)
                    // 如果p是根节点，那么r成为新的根节点，且设为黑色
                    (root = r).red = false;
                else if (pp.left == p)  // 如果p是pp的左子节点
                    pp.left = r;  // 将pp的左子节点设为r
                else  // 如果p是pp的右子节点
                    pp.right = r;  // 将pp的右子节点设为r

                // ===== 第3步：完成旋转 =====
                r.left = p;  // 将p设为r的左子节点
                p.parent = r;  // 更新p的父节点为r
            }
            return root;  // 返回根节点（可能变化）
        }

        /**
         * 红黑树右旋操作
         * 将节点p向左上方旋转，让它的左子节点l旋转到p的位置
         *
         * 右旋前：      右旋后：
         *     p           l
         *    / \         / \
         *   l   c   →   a   p
         *  / \             / \
         * a   b           b   c
         *
         * @param root 当前根节点
         * @param p 要旋转的节点
         * @return 新的根节点（可能变化）
         */
        static <K,V> TreeNode<K,V> rotateRight(TreeNode<K,V> root,
                                               TreeNode<K,V> p) {
            TreeNode<K,V> l, pp, lr;  // l: p的左子节点, pp: p的父节点, lr: l的右子节点

            // 检查是否可以右旋：p存在且p的左子节点l存在
            if (p != null && (l = p.left) != null) {

                // ===== 第1步：处理l的右子节点 =====
                // 将l的右子节点(lr)移给p作为左子节点
                if ((lr = p.left = l.right) != null)
                    lr.parent = p;  // 如果lr存在，更新它的父节点为p

                // ===== 第2步：处理p的父节点 =====
                // 将l的父节点设为p的父节点
                if ((pp = l.parent = p.parent) == null)
                    // 如果p是根节点，那么l成为新的根节点，且设为黑色
                    (root = l).red = false;
                else if (pp.right == p)  // 如果p是pp的右子节点
                    pp.right = l;  // 将pp的右子节点设为l
                else  // 如果p是pp的左子节点
                    pp.left = l;  // 将pp的左子节点设为l

                // ===== 第3步：完成旋转 =====
                l.right = p;  // 将p设为l的右子节点
                p.parent = l;  // 更新p的父节点为l
            }
            return root;  // 返回根节点（可能变化）
        }

        /**
         * 插入节点后的红黑树平衡调整方法
         * @param root 当前的根节点
         * @param x 新插入的节点（可能是需要调整的节点）
         * @return 调整后的新根节点
         */
        static <K,V> TreeNode<K,V> balanceInsertion(TreeNode<K,V> root,
                                                    TreeNode<K,V> x) {
            // ===== 第1行：将新插入的节点设为红色 =====
            x.red = true;
            // 为什么要设红色？因为插入红色节点不会改变路径上的黑色节点数量（性质5）
            // 只可能破坏"不能有连续红色节点"的性质（性质4），这样只需要处理这一种情况

            // ===== 第2行：无限循环，直到树平衡 =====
            // xp: x的父节点 (x parent)
            // xpp: x的祖父节点 (x parent parent)
            // xppl: x祖父的左子节点 (x parent parent left)
            // xppr: x祖父的右子节点 (x parent parent right)
            for (TreeNode<K,V> xp, xpp, xppl, xppr;;) {

                // ===== 第3-6行：情况1 - x是根节点 =====
                if ((xp = x.parent) == null) {  // 如果x没有父节点，说明x是根节点
                    x.red = false;               // 根节点必须为黑色（性质2）
                    return x;                     // 返回x作为新根节点
                }

                // ===== 第7-9行：情况2 - 不需要调整 =====
                // 如果父节点是黑色，或者祖父节点不存在（x的父节点是根）
                else if (!xp.red || (xpp = xp.parent) == null)
                    return root;  // 没有破坏红黑树性质，直接返回原根节点
                // 为什么？因为：
                // 1. 父节点是黑色：插入红色子节点不会造成连续红色，完美！
                // 2. 祖父节点不存在：父节点是根（黑色），也不会造成连续红色

                // ===== 第10行：判断父节点是祖父的左子节点还是右子节点 =====
                // 如果父节点是祖父的左子节点
                if (xp == (xppl = xpp.left)) {

                    // ===== 第11-16行：情况3 - 叔叔节点是红色 =====
                    // xppr是叔叔节点（祖父的右子节点）
                    if ((xppr = xpp.right) != null && xppr.red) {
                        xppr.red = false;  // 叔叔变黑
                        xp.red = false;     // 父节点变黑
                        xpp.red = true;     // 祖父变红
                        x = xpp;            // 将x指向祖父节点，继续向上调整
                        // 为什么这样处理？
                        // 因为父和叔都是红，祖父是黑，这样调整后：
                        // 1. 消除了连续的红色（父和子）
                        // 2. 祖父变红后，该子树的黑高不变
                        // 3. 但祖父变红可能和曾祖父形成连续红色，所以需要继续循环
                    }

                    // ===== 第17-28行：情况4 - 叔叔节点是黑色或不存在 =====
                    else {
                        // ===== 第18-21行：情况4.1 - x是父节点的右子节点（需要先左旋）=====
                        if (x == xp.right) {
                            // 对父节点进行左旋，变成x是左子节点的情况
                            root = rotateLeft(root, x = xp);
                            // 旋转后更新引用关系
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        // ===== 第22-27行：情况4.2 - x是父节点的左子节点（或经过左旋后）=====
                        if (xp != null) {      // 如果父节点存在
                            xp.red = false;     // 父节点变黑
                            if (xpp != null) {  // 如果祖父节点存在
                                xpp.red = true;  // 祖父节点变红
                                // 对祖父节点进行右旋
                                root = rotateRight(root, xpp);
                            }
                        }
                        // 为什么这样处理？
                        // 这种情况类似于：父红、叔黑（或null）、x是内侧子节点
                        // 通过旋转和变色，消除连续红色同时保持二叉搜索树性质
                    }
                }

                // ===== 第29-48行：情况5 - 父节点是祖父的右子节点（对称情况）=====
                else {
                    // 叔叔节点是祖父的左子节点
                    if (xppl != null && xppl.red) {  // 情况5.1：叔叔是红色
                        xppl.red = false;  // 叔叔变黑
                        xp.red = false;     // 父节点变黑
                        xpp.red = true;     // 祖父变红
                        x = xpp;            // 继续向上调整
                    }
                    else {  // 情况5.2：叔叔是黑色或不存在
                        // ===== 第36-39行：情况5.2.1 - x是父节点的左子节点（需要先右旋）=====
                        if (x == xp.left) {
                            // 对父节点进行右旋，变成x是右子节点的情况
                            root = rotateRight(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        // ===== 第40-45行：情况5.2.2 - x是父节点的右子节点（或经过右旋后）=====
                        if (xp != null) {
                            xp.red = false;  // 父节点变黑
                            if (xpp != null) {
                                xpp.red = true;  // 祖父变红
                                // 对祖父节点进行左旋
                                root = rotateLeft(root, xpp);
                            }
                        }
                    }
                }
                // 循环继续，直到树平衡
            }
        }

        /**
         * 删除节点后的平衡操作
         * 当从红黑树中删除一个黑色节点时，会破坏性质5（黑高相等），
         * 这个方法通过旋转和变色来恢复平衡。
         *
         * @param root 当前根节点
         * @param x 需要平衡的节点（通常是替换被删除节点的那个节点，或者其子节点）
         * @return 新的根节点
         */
        static <K,V> TreeNode<K,V> balanceDeletion(TreeNode<K,V> root,
                                                   TreeNode<K,V> x) {
            // 无限循环，直到平衡完成
            for (TreeNode<K,V> xp, xpl, xpr;;) {
                // ===== 情况1：x为null或是根节点 =====
                if (x == null || x == root)
                    return root;  // 不需要调整，直接返回

                    // ===== 情况2：x的父节点为null（x是根节点） =====
                else if ((xp = x.parent) == null) {
                    x.red = false;  // 根节点设为黑色
                    return x;       // 返回x作为新根
                }

                // ===== 情况3：x是红色节点 =====
                else if (x.red) {
                    x.red = false;  // 将红色变为黑色，直接补偿了删除的黑色节点
                    return root;    // 平衡完成
                }

                // ===== 情况4：x是黑色节点，需要复杂调整 =====
                // 根据x是父节点的左子节点还是右子节点，分为对称的两种情况
                else if ((xpl = xp.left) == x) {  // x是左子节点
                    // ===== 情况4.1：x的兄弟节点xpr是红色 =====
                    if ((xpr = xp.right) != null && xpr.red) {
                        // 通过左旋将情况转化为兄弟为黑色的情况
                        xpr.red = false;     // 兄弟变黑
                        xp.red = true;       // 父节点变红
                        root = rotateLeft(root, xp);  // 左旋
                        // 更新引用：重新获取xp（可能已变）和xpr
                        xpr = (xp = x.parent) == null ? null : xp.right;
                    }

                    // ===== 情况4.2：兄弟节点不存在 =====
                    if (xpr == null)
                        x = xp;  // 将问题向上推，继续处理父节点
                    else {
                        // ===== 情况4.3：兄弟节点是黑色，且它的两个子节点都是黑色 =====
                        TreeNode<K,V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) &&
                                (sl == null || !sl.red)) {
                            xpr.red = true;  // 兄弟变红
                            x = xp;          // 问题向上推
                        }
                        else {
                            // ===== 情况4.4：兄弟节点的右子节点是黑色（左子节点是红色）=====
                            if (sr == null || !sr.red) {
                                if (sl != null)
                                    sl.red = false;  // 左子节点变黑
                                xpr.red = true;      // 兄弟变红
                                // 右旋兄弟节点，转化为兄弟的右子为红色的情况
                                root = rotateRight(root, xpr);
                                // 更新引用
                                xpr = (xp = x.parent) == null ?
                                        null : xp.right;
                            }

                            // ===== 情况4.5：兄弟节点的右子节点是红色 =====
                            if (xpr != null) {
                                // 兄弟节点颜色设为父节点颜色
                                xpr.red = (xp == null) ? false : xp.red;
                                if ((sr = xpr.right) != null)
                                    sr.red = false;  // 右子节点变黑
                            }
                            if (xp != null) {
                                xp.red = false;      // 父节点变黑
                                // 左旋父节点
                                root = rotateLeft(root, xp);
                            }
                            x = root;  // 平衡完成
                        }
                    }
                }
                else { // 对称情况：x是右子节点（代码结构与上面完全对称，只是左右互换）
                    // 注释同上，只是左右方向相反
                    if (xpl != null && xpl.red) {  // 兄弟是红色
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
                                (sr == null || !sr.red)) {  // 兄弟的两个子节点都是黑色
                            xpl.red = true;
                            x = xp;
                        }
                        else {
                            if (sl == null || !sl.red) {  // 兄弟的左子节点是黑色
                                if (sr != null)
                                    sr.red = false;
                                xpl.red = true;
                                root = rotateLeft(root, xpl);
                                xpl = (xp = x.parent) == null ?
                                        null : xp.left;
                            }
                            if (xpl != null) {  // 兄弟的左子节点是红色
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
         * 递归检查链表正确性和红黑树正确性
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
    }

}

```



### **3.2LinkedHashMap**
- 3.2.5.1 维护访问顺序
- 3.2.5.2 LRU缓存实现

### 3.3 TreeMap
- 3.3.1 红黑树实现
- 3.3.2 排序功能
- 3.3.3 范围查询

### 3.4 Hashtable
- 3.4.1 线程安全实现
- 3.4.2 Properties类

### 3.5 并发Map
#### **ConcurrentHashMap**
- 3.5.1.1 JDK1.7分段锁
- 3.5.1.2 JDK1.8 CAS优化
- 3.5.1.3 并发性能

#### **ConcurrentSkipListMap**
- 3.5.2.1 跳表实现
- 3.5.2.2 并发有序Map

