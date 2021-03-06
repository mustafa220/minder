package controllers

import java.util
import java.util.Date

import builtin.ReportGenerator
import controllers.common.Utils
import minderengine._
import models.{Wrapper, _}
import mtdl._
import play.Logger

import scala.collection.JavaConversions._
import scala.io.Source

/**
  * This class starts a test in a new actor and
  * provides information to the main actor (status)
  * Created by yerlibilgin on 13/01/15.
  */
class TestRunContext(val testRun: TestRun, testRunFeeder: TestRunFeeder, testLogFeeder: TestLogFeeder, testEngine: TestEngine) extends Runnable with TestProcessWatcher {
  var suspended = false;
  val variableWrapperMapping = collection.mutable.Map[String, MappedWrapper]();
  val mappedWrappers = MappedWrapper.findByJob(testRun.job)
  var sutNames: java.util.Set[String] = null;
  var error = ""
  var job: AbstractJob = AbstractJob.findById(testRun.job.id)
  val user = testRun.runner;
  val tdl = Tdl.findById(job.tdl.id);
  val testCase = TestCase.findById(tdl.testCase.id)
  val testAssertion = TestAssertion.findById(testCase.testAssertion.id)
  val testGroup = TestGroup.findById(testAssertion.testGroup.id)
  job.tdl = tdl;
  tdl.testCase = testCase;

  val packageRoot = "_" + testGroup.id;
  val packagePath = packageRoot + "/_" + testCase.id;
  val cls = TdlCompiler.compileTdl(packageRoot, packagePath, testGroup.dependencyString, testCase.name, source = tdl.tdl, version = tdl.version);
  var MinderTDL: MinderTdl = null;
  val logStringBuilder = new StringBuilder;
  val reportLogBuilder = new StringBuilder;

  /**
    * Assign this runnable in order to do one last job (set labels ...) before finishing this test run
    */
  var finalRunnable: Runnable = null;

  var sessionID: String = Utils.getCurrentTimeStamp;

  /**
    * Number of steps that will be calculated at the beginning for percentage
    * calculation.
    */
  var totalSteps = 1 //for divide by zero

  /**
    * Where we are?
    *
    * The progress will be calculated as: progress = currentStep * 100 / totalSteps
    */
  var currentStep = 0;

  /**
    * progress = currentStep * 100 / totalSteps
    */
  var progressPercent = 0;

  var gitbReplyToUrlAddress = "";

  for (mappedWrapper: MappedWrapper <- mappedWrappers) {
    mappedWrapper.parameter = WrapperParam.findById(mappedWrapper.parameter.id);
    mappedWrapper.wrapperVersion = WrapperVersion.findById(mappedWrapper.wrapperVersion.id);
    mappedWrapper.wrapperVersion.wrapper = Wrapper.findById(mappedWrapper.wrapperVersion.wrapper.id);
    variableWrapperMapping.put(mappedWrapper.parameter.name, mappedWrapper)
  }


  val rg = new ReportGenerator {
    override def getCurrentTestUserInfo: UserDTO = null

    override def getSUTIdentifiers: SUTIdentifiers = null
  }

  rg.startTest()
  rg.setReportTemplate(Source.fromInputStream(this.getClass.getResourceAsStream("/taReport.xml")).mkString.getBytes())
  rg.setReportAuthor(user.name, user.email);

  describe(TestEngine.describe(cls))

  override def run(): Unit = {
    testRun.status = TestRunStatus.IN_PROGRESS
    testRun.save()
    testEngine.runTest(sessionID, user.email, cls, variableWrapperMapping, TestRunContext.this, job.mtdlParameters)
  }

  /**
    * This callback comes from the engine so that we can create our status data structure and later update it.
    *
    * @param slotDefs
    */
  def describe(slotDefs: util.List[Rivet]): Unit = {
    totalSteps = slotDefs.size() * 2; //one rivet call, one rivet finished
  }

  def stepUp(): Unit = {
    currentStep += 1
    progressPercent = currentStep * 100 / totalSteps
    if (progressPercent > 100)
      progressPercent = 100

    testRunFeeder.testProgressUpdate(progressPercent)
  }

  override def rivetFinished(rivetIndex: Int): Unit = {
    stepUp()
    Logger.info("Rivet " + rivetIndex + " finished")
  }

  override def rivetInvoked(rivetIndex: Int): Unit = {
    stepUp()
    Logger.info("Rivet " + rivetIndex + " finished")
  }

  override def signalEmitted(rivetIndex: Int, signalIndex: Int, signalData: SignalData): Unit = {
  }

  override def finished() {
    testRun.status = TestRunStatus.SUCCESS
    updateRun()
  }

  override def failed(err: String, t: Throwable) {
    error = err
    testRun.status = TestRunStatus.FAILED
    updateRun()
  }

  override def addLog(log: String): Unit = {
    logStringBuilder.append(log)
    testLogFeeder.log(LogRecord(testRun, log))
  }

  override def addReportLog(log: String): Unit = {
    reportLogBuilder.append(log)
    logStringBuilder.append(log)
    testLogFeeder.log(LogRecord(testRun, log))
  }

  def updateRun(): Unit = {
    rg.setTestDetails(testGroup, testAssertion, testCase, job, sutNames, reportLogBuilder.toString())
    testRun.finishDate = new Date();
    testRun.history.setSystemOutputLog(logStringBuilder.toString());
    testRun.report = rg.generateReport();
    testRun.errorMessage = error.getBytes("utf-8");
    if (sutNames != null) {
      val sb = new StringBuilder()
      var i: Int = 1
      for (sut <- sutNames) {
        sb.append(i).append("- ").append(sut).append('\n')
        i += 1
      }
      testRun.sutNames = sb.toString();
    }

    //if there is anything to be done before the last save action, do it
    if (finalRunnable != null)
      finalRunnable.run()

    testRun.save()
  }

  override def updateSUTNames(set: scala.collection.Set[String]): Unit = {
    sutNames = set;
  }

  def init() = {
    testRun.number = TestRun.getMaxNumber() + 1
    testRun.status = TestRunStatus.IN_PROGRESS;
    testRun.date = new Date();
  }

  def initialize(): MinderTdl = {
    if (this.MinderTDL == null) {
      this.MinderTDL = cls.getConstructors()(0).newInstance(java.lang.Boolean.FALSE).asInstanceOf[MinderTdl];
    }
    MinderTDL;
  }


  def suspend() = {
    this.suspended = true
  }

  def resume() = {
    this.suspended = false
  }

  def isSuspended(): Boolean = suspended


}
