#!/usr/bin/env python
#
# Biopet is built on top of GATK Queue for building bioinformatic
# pipelines. It is mainly intended to support LUMC SHARK cluster which is running
# SGE. But other types of HPC that are supported by GATK Queue (such as PBS)
# should also be able to execute Biopet tools and pipelines.
#
# Copyright 2014 Sequencing Analysis Support Core - Leiden University Medical Center
#
# Contact us at: sasc@lumc.nl
#
# A dual licensing mode is applied. The source code within this project that are
# not part of GATK Queue is freely available for non-commercial use under an AGPL
# license; For commercial users or users who do not want to follow the AGPL
# license, please contact us to obtain a separate license.
#

from __future__ import print_function

import argparse
import json
import locale
import os
import re
import sys
from os import path

from jinja2 import Environment, FileSystemLoader


# set locale for digit grouping
locale.setlocale(locale.LC_ALL, "")


class FastQCModule(object):

    """Class representing a FastQC analysis module."""

    def __init__(self, raw_lines, end_mark='>>END_MODULE'):
        """

        :param raw_lines: list of lines in the module
        :type raw_lines: list of str
        :param end_mark: mark of the end of the module
        :type end_mark: str

        """
        self.raw_lines = raw_lines
        self.end_mark = end_mark
        self._status = None
        self._name = None
        self._data = self._parse()

    def __repr__(self):
        return '%s(%s)' % (self.__class__.__name__,
                '[%r, ...]' % self.raw_lines[0])

    def __str__(self):
        return ''.join(self.raw_lines)

    @property
    def name(self):
        """Name of the module."""
        return self._name

    @property
    def columns(self):
        """Columns in the module."""
        return self._columns

    @property
    def data(self):
        """FastQC data."""
        return self._data

    @property
    def status(self):
        """FastQC run status."""
        return self._status

    def _parse(self):
        """Common parser for a FastQC module."""
        # check that the last line is a proper end mark
        assert self.raw_lines[-1].startswith(self.end_mark)
        # parse name and status from first line
        tokens = self.raw_lines[0].strip().split('\t')
        name = tokens[0][2:]
        self._name = name
        status = tokens[-1]
        assert status in ('pass', 'fail', 'warn'), "Unknown module status: %r" \
            % status
        self._status = status
        # and column names from second line
        columns = self.raw_lines[1][1:].strip().split('\t')
        self._columns = columns
        # the rest of the lines except the last one
        data = []
        for line in self.raw_lines[2:-1]:
            cols = line.strip().split('\t')
            data.append(cols)

        # optional processing for different modules
        if self.name == 'Basic Statistics':
            data = {k: v for k, v in data}

        return data


class FastQC(object):

    """Class representing results from a FastQC run."""

    # module name -- attribute name mapping
    _mod_map = {
        '>>Basic Statistics': 'basic_statistics',
        '>>Per base sequence quality': 'per_base_sequence_quality',
        '>>Per sequence quality scores': 'per_sequence_quality_scores',
        '>>Per base sequence content': 'per_base_sequence_content',
        '>>Per base GC content': 'per_base_gc_content',
        '>>Per sequence GC content': 'per_sequence_gc_content',
        '>>Per base N content': 'per_base_n_content',
        '>>Sequence Length Distribution': 'sequence_length_distribution',
        '>>Sequence Duplication Levels': 'sequence_duplication_levels',
        '>>Overrepresented sequences': 'overrepresented_sequences',
        '>>Kmer content': 'kmer_content',
    }

    def __init__(self, fname):
        """

        :param fp: open file handle pointing to the FastQC data file
        :type fp: file handle

        """
        # get file name
        self.fname = fname
        self._modules = {}

        with open(fname, "r") as fp:
            line = fp.readline()
            while True:

                tokens = line.strip().split('\t')
                # break on EOF
                if not line:
                    break
                # parse version
                elif line.startswith('##FastQC'):
                    self.version = line.strip().split()[1]
                # parse individual modules
                elif tokens[0] in self._mod_map:
                    attr = self._mod_map[tokens[0]]
                    raw_lines = self._read_module(fp, line, tokens[0])
                    self._modules[attr] = FastQCModule(raw_lines)

                line = fp.readline()

    def __repr__(self):
        return '%s(%r)' % (self.__class__.__name__, self.fname)

    def _filter_by_status(self, status):
        """Filter out modules whose status is different from the given status.

        :param status: module status
        :type status: str
        :returns: a list of FastQC module names with the given status
        :rtype: list of str

        """
        return [x.name for x in self._modules.values() if x.status == status]

    def _read_module(self, fp, line, start_mark):
        """Returns a list of lines in a module.

        :param fp: open file handle pointing to the FastQC data file
        :type fp: file handle
        :param line: first line in the module
        :type line: str
        :param start_mark: string denoting start of the module
        :type start_mark: str
        :returns: a list of lines in the module
        :rtype: list of str

        """
        raw = [line]
        while not line.startswith('>>END_MODULE'):
            line = fp.readline()
            raw.append(line)

            if not line:
                raise ValueError("Unexpected end of file in module %r" % line)

        return raw

    @property
    def modules(self):
        """All modules in the FastQC results."""
        return self._modules

    @property
    def passes(self):
        """All module names that pass QC."""
        return self._filter_by_status('pass')

    @property
    def passes_num(self):
        """How many modules have pass status."""
        return len(self.passes)

    @property
    def warns(self):
        """All module names with warning status."""
        return self._filter_by_status('warn')

    @property
    def warns_num(self):
        """How many modules have warn status."""
        return len(self.warns)

    @property
    def fails(self):
        """All names of failed modules."""
        return self._filter_by_status('fail')

    @property
    def fails_num(self):
        """How many modules failed."""
        return len(self.fails)

    @property
    def basic_statistics(self):
        """Basic statistics module results."""
        return self._modules['basic_statistics']

    @property
    def per_base_sequence_quality(self):
        """Per base sequence quality module results."""
        return self._modules['per_base_sequence_quality']

    @property
    def per_sequence_quality_scores(self):
        """Per sequence quality scores module results."""
        return self._modules['per_sequence_quality_scores']

    @property
    def per_base_sequence_content(self):
        """Per base sequence content module results."""
        return self._modules['per_base_sequence_content']

    @property
    def per_base_gc_content(self):
        """Per base GC content module results."""
        return self._modules['per_base_gc_content']

    @property
    def per_sequence_gc_content(self):
        """Per sequence GC content module results."""
        return self._modules['per_sequence_gc_content']

    @property
    def per_base_n_content(self):
        """Per base N content module results."""
        return self._modules['per_base_n_content']

    @property
    def sequence_length_distribution(self):
        """Per sequence length distribution module results."""
        return self._modules['sequence_length_distribution']

    @property
    def sequence_duplication_levels(self):
        """Sequence duplication module results."""
        return self._modules['sequence_duplication_levels']

    @property
    def overrepresented_sequences(self):
        """Overrepresented sequences module results."""
        return self._modules['overrepresented_sequences']

    @property
    def kmer_content(self):
        """Kmer content module results."""
        return self._modules['kmer_content']


# HACK: remove this and use jinja2 only for templating
class LongTable(object):

    """Class representing a longtable in LaTeX."""

    def __init__(self, caption, label, header, aln, colnum):
        self.lines = [
            "\\begin{center}",
            "\\captionof{table}{%s}" % caption,
            "\\label{%s}" % label,
            "\\begin{longtable}{%s}" % aln,
            "\\hline",
            "%s" % header,
            "\\hline \\hline",
            "\\endhead",
            "\\hline \\multicolumn{%i}{c}{\\textit{Continued on next page}}\\\\" % \
                    colnum,
            "\\hline",
            "\\endfoot",
            "\\hline",
            "\\endlastfoot",
        ]

    def __str__(self):
        return "\n".join(self.lines)

    def add_row(self, row):
        self.lines.append(row)

    def end(self):
        self.lines.extend(["\\end{longtable}", "\\end{center}",
            "\\addtocounter{table}{-1}"])


# filter functions for the jinja environment
def nice_int(num, default="None"):
    if num is None:
        return default
    try:
        return locale.format("%i", int(num), grouping=True)
    except:
        return default


def nice_flt(num, default="None"):
    if num is None:
        return default
    try:
        return locale.format("%.2f", float(num), grouping=True)
    except:
        return default


def float2nice_pct(num, default="None"):
    if num is None:
        return default
    try:
        return locale.format("%.2f", float(num) * 100.0, grouping=True)
    except:
        return default


# and some handy functions
def natural_sort(inlist):
    key = lambda x: [int(a) if a.isdigit() else a.lower() for a in
            re.split("([0-9]+)", x)]
    inlist.sort(key=key)
    return inlist


def write_template(run, template_file, logo_file):

    template_file = path.abspath(path.realpath(template_file))
    template_dir = path.dirname(template_file)
    # spawn environment and create output directory
    env = Environment(loader=FileSystemLoader(template_dir))

    # change delimiters since LaTeX may use "{{", "{%", or "{#"
    env.block_start_string = "((*"
    env.block_end_string = "*))"
    env.variable_start_string = "((("
    env.variable_end_string = ")))"
    env.comment_start_string = "((="
    env.comment_end_string = "=))"

    # trim all block-related whitespaces
    env.trim_blocks = True
    env.lstrip_blocks = True

    # put in out filter functions
    env.filters["nice_int"] = nice_int
    env.filters["nice_flt"] = nice_flt
    env.filters["float2nice_pct"] = float2nice_pct
    env.filters["basename"] = path.basename

    # write tex template for pdflatex
    jinja_template = env.get_template(path.basename(template_file))
    run.logo = logo_file
    render_vars = {
        "run": run,
    }
    rendered = jinja_template.render(**render_vars)

    print(rendered, file=sys.stdout)


class GentrapLib(object):

    def __init__(self, run, sample, name, summary):
        assert isinstance(run, GentrapRun)
        assert isinstance(sample, GentrapSample)
        self.run = run
        self.sample = sample
        self.name = name
        self._raw = summary
        # flexiprep settings
        self.flexiprep = summary.get("flexiprep", {})
        self.flexiprep_files = summary.get("flexiprep", {}).get("files", {}).get("pipeline", {})
        self.clipping = not self.flexiprep["settings"]["skip_clip"]
        self.trimming = not self.flexiprep["settings"]["skip_trim"]
        self.is_paired_end = self.flexiprep["settings"]["paired"]
        if "fastqc_R1" in self.flexiprep["files"]:
            self.fastqc_r1_files = self.flexiprep["files"]["fastqc_R1"]
            self.fastqc_r1 = FastQC(self.fastqc_r1_files["fastqc_data"]["path"])
        if "fastqc_R2" in self.flexiprep["files"]:
            self.fastqc_r2_files = self.flexiprep["files"]["fastqc_R2"]
            self.fastqc_r2 = FastQC(self.fastqc_r2_files["fastqc_data"]["path"])
        if "fastqc_R1_qc" in self.flexiprep["files"]:
            self.fastqc_r1_qc_files = self.flexiprep["files"]["fastqc_R1_qc"]
            self.fastqc_r1_qc = FastQC(self.fastqc_r1_qc_files["fastqc_data"]["path"])
        if "fastqc_R2_qc" in self.flexiprep["files"]:
            self.fastqc_r2_qc_files = self.flexiprep["files"]["fastqc_R2_qc"]
            self.fastqc_r2_qc = FastQC(self.fastqc_r2_qc_files["fastqc_data"]["path"])
        # mapping metrics settings
        self.aln_metrics = summary.get("bammetrics", {}).get("stats", {}).get("alignment_metrics", {})
        # insert size metrics files
        self.inserts_metrics_files = summary.get("bammetrics", {}).get("files", {}).get("insert_size_metrics", {})
        # rna metrics files and stats
        self.rna_metrics_files = summary.get("gentrap", {}).get("files", {}).get("rna_metrics", {})
        _rmetrics = summary.get("gentrap", {}).get("stats", {}).get("rna_metrics", {})
        if _rmetrics:
            self.rna_metrics = {k: v for k, v in _rmetrics.items() }
            pf_bases = float(_rmetrics["pf_bases"])
            exonic_bases = int(_rmetrics.get("coding_bases", 0)) + int(_rmetrics.get("utr_bases", 0))
            # picard uses pct_ but it's actually ratio ~ we follow their convention
            pct_exonic_bases_all = exonic_bases / float(_rmetrics["pf_bases"])
            pct_exonic_bases = exonic_bases / float(_rmetrics.get("pf_aligned_bases", 0))
            self.rna_metrics.update({
                    "exonic_bases": exonic_bases,
                    "pct_exonic_bases_all": pct_exonic_bases_all,
                    "pct_exonic_bases": pct_exonic_bases,
                    "pct_aligned_bases": 1.0,
                    "pct_aligned_bases_all": float(_rmetrics.get("pf_aligned_bases", 0.0)) / pf_bases,
                    "pct_coding_bases_all": float(_rmetrics.get("coding_bases", 0.0)) / pf_bases,
                    "pct_utr_bases_all": float(_rmetrics.get("utr_bases", 0.0)) / pf_bases,
                    "pct_intronic_bases_all": float(_rmetrics.get("intronic_bases", 0.0)) / pf_bases,
                    "pct_intergenic_bases_all": float(_rmetrics.get("intergenic_bases", 0.0)) / pf_bases,
            })
            if _rmetrics.get("ribosomal_bases", "") != "":
                self.rna_metrics["pct_ribosomal_bases_all"] = float(_rmetrics.get("pf_ribosomal_bases", 0.0)) / pf_bases

    def __repr__(self):
        return "{0}(sample=\"{1}\", lib=\"{2}\")".format(
                self.__class__.__name__, self.sample.name, self.name)


class GentrapSample(object):

    def __init__(self, run, name, summary):
        assert isinstance(run, GentrapRun)
        self.run = run
        self.name = name
        self._raw = summary
        self.is_paired_end = summary.get("gentrap", {}).get("stats", {}).get("pipeline", {})["all_paired"]
        # mapping metrics settings
        self.aln_metrics = summary.get("bammetrics", {}).get("stats", {}).get("alignment_metrics", {})
        # insert size metrics files
        self.inserts_metrics_files = summary.get("bammetrics", {}).get("files", {}).get("insert_size_metrics", {})
        # rna metrics files and stats
        self.rna_metrics_files = summary.get("gentrap", {}).get("files", {}).get("rna_metrics", {})
        _rmetrics = summary.get("gentrap", {}).get("stats", {}).get("rna_metrics", {})
        if _rmetrics:
            self.rna_metrics = {k: v for k, v in _rmetrics.items() }
            pf_bases = float(_rmetrics["pf_bases"])
            exonic_bases = int(_rmetrics.get("coding_bases", 0)) + int(_rmetrics.get("utr_bases", 0))
            # picard uses pct_ but it's actually ratio ~ we follow their convention
            pct_exonic_bases_all = exonic_bases / float(_rmetrics["pf_bases"])
            pct_exonic_bases = exonic_bases / float(_rmetrics.get("pf_aligned_bases", 0))
            self.rna_metrics.update({
                    "exonic_bases": exonic_bases,
                    "pct_exonic_bases_all": pct_exonic_bases_all,
                    "pct_exonic_bases": pct_exonic_bases,
                    "pct_aligned_bases": 1.0,
                    "pct_aligned_bases_all": float(_rmetrics.get("pf_aligned_bases", 0.0)) / pf_bases,
                    "pct_coding_bases_all": float(_rmetrics.get("coding_bases", 0.0)) / pf_bases,
                    "pct_utr_bases_all": float(_rmetrics.get("utr_bases", 0.0)) / pf_bases,
                    "pct_intronic_bases_all": float(_rmetrics.get("intronic_bases", 0.0)) / pf_bases,
                    "pct_intergenic_bases_all": float(_rmetrics.get("intergenic_bases", 0.0)) / pf_bases,
            })
            if self.run.settings["strand_protocol"] != "non_specific":
                self.rna_metrics.update({
                })
            if _rmetrics.get("ribosomal_bases", "") != "":
                self.rna_metrics["pct_ribosomal_bases_all"] = float(_rmetrics.get("pf_ribosomal_bases", 0.0)) / pf_bases

        self.lib_names = sorted(summary["libraries"].keys())
        self.libs = \
            {l: GentrapLib(self.run, self, l, summary["libraries"][l]) \
                for l in self.lib_names}

    def __repr__(self):
        return "{0}(\"{1}\")".format(self.__class__.__name__, self.name)


class GentrapRun(object):

    def __init__(self, summary_file):

        with open(summary_file, "r") as src:
            summary = json.load(src)

        self._raw = summary
        self.summary_file = summary_file

        self.files = summary["gentrap"].get("files", {}).get("pipeline", {})
        self.settings = summary["gentrap"]["settings"]
        self.version = self.settings.get("version", "unknown")
        # list containing all exes
        self.all_executables = summary["gentrap"]["executables"]
        # list containing exes we want to display
        executables = [
            ("cutadapt", "adapter clipping"),
            ("sickle", "base quality trimming"),
            ("fastqc", "sequence metrics collection"),
            ("gsnap", "alignment"),
            ("tophat", "alignment"),
            ("star", "alignment"),
            ("htseqcount", "fragment counting"),
        ]
        self.executables = {}
        for k, desc in executables:
            in_summary = self.all_executables.get(k)
            if in_summary is not None:
                self.executables[k] = in_summary
                self.executables[k]["desc"] = desc
        # since we get the version from the tools we use
        if self.all_executables.get("collectalignmentsummarymetrics") is not None:
            self.executables["picard"] = self.all_executables["collectalignmentsummarymetrics"]
            self.executables["picard"]["desc"] = "alignment_metrics_collection"
            # None means we are using the Queue built in Picard
            if self.executables["picard"].get("version") is None:
                self.executables["picard"]["version"] = "built-in"
        # since we get the version from the sub tools we use
        if self.all_executables.get("samtoolsview") is not None:
            self.executables["samtools"] = self.all_executables["samtoolsview"]
            self.executables["samtools"]["desc"] = "various post-alignment processing"

        self.sample_names = sorted(summary["samples"].keys())
        self.samples = \
            {s: GentrapSample(self, s, summary["samples"][s]) \
                for s in self.sample_names}
        self.libs = []
        for sample in self.samples.values():
            self.libs.extend(sample.libs.values())
        if all([s.is_paired_end for s in self.samples.values()]):
            self.lib_type = "all paired end"
        elif all([not s.is_paired_end for s in self.samples.values()]):
            self.lib_type = "all single end"
        else:
            self.lib_type = "mixed (single end and paired end)"

    def __repr__(self):
        return "{0}(\"{1}\")".format(self.__class__.__name__,
                                        self.summary_file)


if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument("summary_file", type=str,
            help="Path to Gentrap summary file")
    parser.add_argument("template_file", type=str,
            help="Path to main template file")
    parser.add_argument("logo_file", type=str,
            help="Path to main logo file")
    args = parser.parse_args()

    run = GentrapRun(args.summary_file)
    write_template(run, args.template_file, args.logo_file)
