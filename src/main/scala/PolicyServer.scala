/**
 *  Copyright (C) 2011 Aaron Valade <adv@alum.mit.edu>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gs.policy

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.frame.{Delimiters, DelimiterBasedFrameDecoder}
import org.jboss.netty.handler.codec.string.{StringEncoder, StringDecoder}
import org.jboss.netty.channel._
import java.io.File
import scala.io.Source
import org.slf4j.LoggerFactory

class PolicyServer
object PolicyServer {
  val DEFAULT_FILE_NAME = "MasterPolicy.xml"
  
  def main(args: Array[String]): Unit = {
    new File(args.headOption.getOrElse(DEFAULT_FILE_NAME)) match {
      case f if f.exists => bootstrap(Source.fromFile(f).mkString)
      case f => System.out.println("Policy file %s doesn't exist, please specify a policy file to load".format(f.getAbsolutePath))
    }
  }
  
  def bootstrap(response: String) = {
    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool,
        Executors.newCachedThreadPool
      )
    )
    bootstrap.setPipelineFactory(new PolicyServerPipelineFactory(response))
    try {
      bootstrap.bind(new InetSocketAddress(843))
    } catch {
      case e: ChannelException if e.getMessage.contains("Failed to bind to:") =>
        System.out.println("Unable to bind to port 843, try running the application as a trusted user")
    }
  }
}

class PolicyServerPipelineFactory(response: String) extends ChannelPipelineFactory {
  def getPipeline = {
    val p = Channels.pipeline
    p.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.nulDelimiter:_*))
    p.addLast("decoder", new StringDecoder)
    p.addLast("encoder", new StringEncoder)
    p.addLast("handler", new PolicyHandler(response))
    p
  } 
}

class PolicyHandler(response: String) extends SimpleChannelUpstreamHandler {
  val log = LoggerFactory.getLogger(classOf[PolicyHandler])
  
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
    super.exceptionCaught(ctx, e)
    e.getChannel.close
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    e.getMessage match {
      case m: String if m.toLowerCase.startsWith("<policy-file-request/>") => 
        val future = e.getChannel.write(response)
        future.addListener(ChannelFutureListener.CLOSE)
      case m => log.warn("Received unknown message: %s".format(m))
    }
  }
}
