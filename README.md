# Java8集合框架目录

## 一、集合框架总览

最近在面试，工作了四年了，发现有些底层的数据结构与原理只知道个大概，以前也反复看过很多次，但是一放下就很容易忘记，所以打算系统的读一下。

![](pngs\Collection.png)

![](pngs\Map.png)

## 二、Collection接口体系

### 2.1 List接口
#### **ArrayList源码阅读**

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
- 2.2.1 Set接口特性
  - 无序、不可重复
  - 相等性判断

#### **HashSet**
- 2.2.2.1 基于HashMap实现
- 2.2.2.2 哈希机制
- 2.2.2.3 性能特点

#### **LinkedHashSet**
- 2.2.3.1 维护插入顺序
- 2.2.3.2 实现原理

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

### 3.1 Map接口特性
- 键值对映射
- 键的唯一性

### 3.2 HashMap
- 3.2.1 哈希表原理
- 3.2.2 JDK1.8优化：数组+链表+红黑树
- 3.2.3 扩容机制
- 3.2.4 性能分析

#### **LinkedHashMap**
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

## 四、工具类
- 4.1 Collections工具类
- 4.2 Arrays工具类

## 五、性能对比与选择
- 5.1 List实现对比
- 5.2 Set实现对比
- 5.3 Map实现对比
- 5.4 Queue实现对比
- 5.5 选择指南