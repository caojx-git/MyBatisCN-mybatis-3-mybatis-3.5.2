/**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.io.Resources;

/**
 * @author Clinton Begin
 */
/**
 * 序列化装饰器
 * 在使用 SerializedCache后，每次向缓存中写入对象时，实际写入 的是对象的序列化串;而每次读取对象时，
 * 会将序列化串反序列化后再 返回。通过序列化和反序列化的过程保证了每一次缓存给出的对象都是 一个全新的对象，
 * 对该对象的修改不会影响缓存中的对象。
 */
public class SerializedCache implements Cache {

  private final Cache delegate;

  public SerializedCache(Cache delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 向缓存写入一条信息
   * @param key 信息的键
   * @param object 信息的值
   */
  @Override
  public void putObject(Object key, Object object) {
    if (object == null || object instanceof Serializable) { // 要缓存的数据必须是可以序列化的
      // 将数据序列化后写入缓存
      delegate.putObject(key, serialize((Serializable) object));
    } else { // 要缓存的数据不可序列化
      // 抛出异常
      throw new CacheException("SharedCache failed to make a copy of a non-serializable object: " + object);
    }
  }

  /**
   * 从缓存中读取一条信息
   * @param key 信息的键
   * @return 信息的值
   */
  @Override
  public Object getObject(Object key) {
    // 读取缓存中的序列化串
    Object object = delegate.getObject(key);
    // 反序列化后返回
    return object == null ? null : deserialize((byte[]) object);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  private byte[] serialize(Serializable value) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(value);
      oos.flush();
      return bos.toByteArray();
    } catch (Exception e) {
      throw new CacheException("Error serializing object.  Cause: " + e, e);
    }
  }

  private Serializable deserialize(byte[] value) {
    Serializable result;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(value);
         ObjectInputStream ois = new CustomObjectInputStream(bis)) {
      result = (Serializable) ois.readObject();
    } catch (Exception e) {
      throw new CacheException("Error deserializing object.  Cause: " + e, e);
    }
    return result;
  }

  public static class CustomObjectInputStream extends ObjectInputStream {

    public CustomObjectInputStream(InputStream in) throws IOException {
      super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
      return Resources.classForName(desc.getName());
    }

  }

}
