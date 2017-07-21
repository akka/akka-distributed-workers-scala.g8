/**
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package worker

import akka.actor.ActorSystem
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}

object MasterSingleton {
  def proxyProps(system: ActorSystem) = ClusterSingletonProxy.props(
    settings = ClusterSingletonProxySettings(system).withRole("backend"),
    singletonManagerPath = "/user/master")
}
