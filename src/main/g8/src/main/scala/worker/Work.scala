/**
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package worker

case class Work(workId: String, job: Any)

case class WorkResult(workId: String, result: Any)