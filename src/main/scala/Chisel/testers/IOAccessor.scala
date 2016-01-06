// See LICENSE for license details.

package Chisel.testers

import Chisel._

import scala.collection.mutable
import scala.util.matching.Regex

/**
 * named access and type information about the IO bundle of a module
 * used for building testing harnesses
 */
class IOAccessor(val device_io: Bundle, verbose: Boolean = true) {
  val dut_inputs  = device_io.flatten.filter( port => port.dir == INPUT)
  val dut_outputs = device_io.flatten.filter( port => port.dir == OUTPUT)
  val ports_referenced = new mutable.HashSet[Data]

  val referenced_inputs          = new mutable.HashSet[Data]()
  val referenced_outputs         = new mutable.HashSet[Data]()

  val name_to_decoupled_port = new mutable.HashMap[String, DecoupledIO[Data]]()
  val name_to_valid_port     = new mutable.HashMap[String, ValidIO[Data]]()

  val port_to_name = {
    val port_to_name_accumulator = new mutable.HashMap[Data, String]()

    def check_decoupled_or_valid(port: Data, name: String): Unit = {
      port match {
        case decoupled_port : DecoupledIO[Data] =>
          name_to_decoupled_port(name) = decoupled_port
        case valid_port : ValidIO[Data] =>
          name_to_valid_port(name) = valid_port
        case _ =>
      }
    }

    def parse_bundle(b: Bundle, name: String = ""): Unit = {
      for ((n, e) <- b.elements) {
        val new_name = name + (if(name.length > 0 ) "." else "" ) + n
        port_to_name_accumulator(e) = new_name

        e match {
          case bb: Bundle     => parse_bundle(bb, new_name)
          case vv: Vec[_]  => parse_vecs(vv, new_name)
          case ee: Element    =>
          case _              =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
        check_decoupled_or_valid(e, new_name)
      }
    }
    def parse_vecs[T<:Data](b: Vec[T], name: String = ""): Unit = {
      for ((e, i) <- b.zipWithIndex) {
        val new_name = name + s"($i)"
        port_to_name_accumulator(e) = new_name

        e match {
          case bb: Bundle     => parse_bundle(bb, new_name)
          case vv: Vec[_]  => parse_vecs(vv, new_name)
          case ee: Element    =>
          case _              =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
        check_decoupled_or_valid(e, new_name)
      }
    }

    parse_bundle(device_io)
    port_to_name_accumulator
  }
  val name_to_port = port_to_name.map(_.swap)

  def show_ports(pattern : Regex): Unit = {
    def order_ports(a: Data, b: Data) : Boolean = {
      port_to_name(a) < port_to_name(b)
    }
    def show_decoupled_code(port_name:String): String = {
      if(name_to_decoupled_port.contains(port_name)) "D"
      else if(name_to_valid_port.contains(port_name)) "V"
      else if(find_parent_decoupled_port_name(port_name).nonEmpty) "D."
      else if(find_parent_valid_port_name(port_name).nonEmpty) "V."
      else ""

    }
    def show_decoupled_parent(port_name:String): String = {
      find_parent_decoupled_port_name(port_name) match {
        case Some(decoupled_name) => s"$decoupled_name"
        case _                    => find_parent_valid_port_name(port_name).getOrElse("")
      }
    }
    def show_dir(dir: Direction) = dir match {
      case INPUT  => "I"
      case OUTPUT => "O"
      case _      => "-"
    }

    println("=" * 80)
    println("Device under test: io bundle")
    println("%3s  %3s  %-4s  %4s   %-25s %s".format(
            "#", "Dir", "D/V", "Used", "Name", "Parent"
    ))
    println("-" * 80)

    for((port,index) <- port_to_name.keys.toList.sortWith(order_ports).zipWithIndex) {
      val port_name = port_to_name(port)
      println("%3d  %3s   %-4s%4s    %-25s %s".format(
        index,
        show_dir(port.dir),
        show_decoupled_code(port_name),
        if(ports_referenced.contains(port)) "y" else "",
        port_name,
        show_decoupled_parent(port_name)
      ))
    }
    if(verbose) {
      println("=" * 80)
    }
  }

  def find_parent_decoupled_port_name(name: String): Option[String] = {
    val possible_parents = name_to_decoupled_port.keys.toList.filter(s => name.startsWith(s))
    if(possible_parents.isEmpty) return None
    possible_parents.sorted.lastOption
  }
  def find_parent_valid_port_name(name: String): Option[String] = {
    val possible_parents = name_to_valid_port.keys.toList.filter(s => name.startsWith(s))
    if(possible_parents.isEmpty) return None
    possible_parents.sorted.lastOption
  }

  def register(port: Data): Unit = {
    ports_referenced += port
  }

  def contains(port: Data) : Boolean = {
    ports_referenced.contains(port)
  }
}
