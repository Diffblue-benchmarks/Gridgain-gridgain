#
# Copyright 2019 GridGain Systems, Inc. and Contributors.
#
# Licensed under the GridGain Community Edition License (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

class RoundRobin:
    """
    Round-robin generator for use with `Client.connect()`. Cycles a node
    list until a maximum number of reconnects is reached (if set).
    """

    def __init__(self, nodes: list, max_reconnects: int=None):
        """
        :param nodes: list of two-tuples of (host, port) format,
        :param max_reconnects: (optional) maximum number of reconnect attempts.
         defaults to None (cycle nodes infinitely).
        """
        self.nodes = nodes
        self.max_reconnects = max_reconnects
        self.node_index = 0
        self.reconnects = 0

    def __iter__(self) -> 'RoundRobin':
        return self

    def __next__(self) -> tuple:
        if self.max_reconnects is not None:
            if self.reconnects >= self.max_reconnects:
                raise StopIteration
            else:
                self.reconnects += 1

        if self.node_index >= len(self.nodes):
            self.node_index = 0
        node = self.nodes[self.node_index]
        self.node_index += 1
        return node
