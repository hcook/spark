/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.storage

import java.io.{File, FileWriter}

import org.apache.spark.shuffle.hash.HashShuffleManager

import scala.collection.mutable
import scala.language.reflectiveCalls

import akka.actor.Props
import com.google.common.io.Files
import org.mockito.Mockito.{mock, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite}

import org.apache.spark.SparkConf
import org.apache.spark.scheduler.LiveListenerBus
import org.apache.spark.serializer.JavaSerializer
import org.apache.spark.util.{AkkaUtils, Utils}
import org.apache.spark.executor.ShuffleWriteMetrics

class DiskBlockManagerSuite extends FunSuite with BeforeAndAfterEach with BeforeAndAfterAll {
  private val testConf = new SparkConf(false)
  private var rootDir0: File = _
  private var rootDir1: File = _
  private var rootDirs: String = _

  val blockManager = mock(classOf[BlockManager])
  when(blockManager.conf).thenReturn(testConf)
  var diskBlockManager: DiskBlockManager = _

  override def beforeAll() {
    super.beforeAll()
    rootDir0 = Files.createTempDir()
    rootDir0.deleteOnExit()
    rootDir1 = Files.createTempDir()
    rootDir1.deleteOnExit()
    rootDirs = rootDir0.getAbsolutePath + "," + rootDir1.getAbsolutePath
    println("Created root dirs: " + rootDirs)
  }

  override def afterAll() {
    super.afterAll()
    Utils.deleteRecursively(rootDir0)
    Utils.deleteRecursively(rootDir1)
  }

  override def beforeEach() {
    val conf = testConf.clone
    conf.set("spark.local.dir", rootDirs)
    diskBlockManager = new DiskBlockManager(blockManager, conf)
  }

  override def afterEach() {
    diskBlockManager.stop()
  }

  test("basic block creation") {
    val blockId = new TestBlockId("test")
    val newFile = diskBlockManager.getFile(blockId)
    writeToFile(newFile, 10)
    assert(diskBlockManager.containsBlock(blockId))
    newFile.delete()
    assert(!diskBlockManager.containsBlock(blockId))
  }

  test("enumerating blocks") {
    val ids = (1 to 100).map(i => TestBlockId("test_" + i))
    val files = ids.map(id => diskBlockManager.getFile(id))
    files.foreach(file => writeToFile(file, 10))
    assert(diskBlockManager.getAllBlocks.toSet === ids.toSet)
  }

  def writeToFile(file: File, numBytes: Int) {
    val writer = new FileWriter(file, true)
    for (i <- 0 until numBytes) writer.write(i)
    writer.close()
  }
}
