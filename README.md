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
- 2.1.4.1 线程安全实现
- 2.1.4.2 遗留问题
- 2.1.4.3 Stack类

#### **CopyOnWriteArrayList**
- 2.1.5.1 写时复制机制
- 2.1.5.2 并发安全性
- 2.1.5.3 适用场景

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